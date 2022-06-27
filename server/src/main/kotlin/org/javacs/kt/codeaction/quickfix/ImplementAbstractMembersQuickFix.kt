package org.javacs.kt.codeaction.quickfix

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledFile
import org.javacs.kt.index.SymbolIndex
import org.javacs.kt.position.offset
import org.javacs.kt.position.position
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

private const val DEFAULT_TAB_SIZE = 4

class ImplementAbstractMembersQuickFix : QuickFix {
    override fun compute(file: CompiledFile, index: SymbolIndex, range: Range, diagnostics: List<Diagnostic>): List<Either<Command, CodeAction>> {
        val diagnostic = findDiagnosticMatch(diagnostics, range)

        val startCursor = offset(file.content, range.start)
        val endCursor = offset(file.content, range.end)
        val kotlinDiagnostics = file.compile.diagnostics
        
        // If the client side and the server side diagnostics contain a valid diagnostic for this range.
        if (diagnostic != null && anyDiagnosticMatch(kotlinDiagnostics, startCursor, endCursor)) {
            // Get the class with the missing members
            val kotlinClass = file.parseAtPoint(startCursor)
            if (kotlinClass is KtClass) {
                // Get the functions that need to be implemented
                val membersToImplement = getAbstractMembersStubs(file, kotlinClass)

                val uri = file.parse.toPath().toUri().toString()
                // Get the padding to be introduced before the member declarations
                val padding = getDeclarationPadding(file, kotlinClass)

                // Get the location where the new code will be placed
                val newMembersStartPosition = getNewMembersStartPosition(file, kotlinClass)
                val bodyAppendBeginning = listOf(TextEdit(Range(newMembersStartPosition, newMembersStartPosition), "{")).takeIf { kotlinClass.hasNoBody() } ?: emptyList()
                val bodyAppendEnd = listOf(TextEdit(Range(newMembersStartPosition, newMembersStartPosition), System.lineSeparator() + "}")).takeIf { kotlinClass.hasNoBody() } ?: emptyList()

                val textEdits = bodyAppendBeginning + membersToImplement.map {
                    // We leave two new lines before the member is inserted
                    val newText = System.lineSeparator() + System.lineSeparator() + padding + it
                    TextEdit(Range(newMembersStartPosition, newMembersStartPosition), newText)
                } + bodyAppendEnd

                val codeAction = CodeAction()
                codeAction.edit = WorkspaceEdit(mapOf(uri to textEdits))
                codeAction.kind = CodeActionKind.QuickFix
                codeAction.title = "Implement abstract members"
                codeAction.diagnostics = listOf(diagnostic)
                return listOf(Either.forRight(codeAction))
            }
        }
        return listOf()
    }
}

fun findDiagnosticMatch(diagnostics: List<Diagnostic>, range: Range) =
    diagnostics.find { diagnosticMatch(it, range, hashSetOf("ABSTRACT_MEMBER_NOT_IMPLEMENTED", "ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED")) }

private fun anyDiagnosticMatch(diagnostics: Diagnostics, startCursor: Int, endCursor: Int) =
    diagnostics.any { diagnosticMatch(it, startCursor, endCursor, hashSetOf("ABSTRACT_MEMBER_NOT_IMPLEMENTED", "ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED")) }

private fun getAbstractMembersStubs(file: CompiledFile, kotlinClass: KtClass) =
    // For each of the super types used by this class
    kotlinClass.superTypeListEntries.mapNotNull {
        // Find the definition of this super type
        val referenceAtPoint = file.referenceExpressionAtPoint(it.startOffset)
        val descriptor = referenceAtPoint?.second

        val classDescriptor = getClassDescriptor(descriptor)
        
        // If the super class is abstract or an interface
        if (null != classDescriptor && (classDescriptor.kind.isInterface || classDescriptor.modality == Modality.ABSTRACT)) {
            val superClassTypeArguments = getSuperClassTypeProjections(file, it)
            classDescriptor.getMemberScope(superClassTypeArguments).getContributedDescriptors().filter { classMember ->
               (classMember is FunctionDescriptor && classMember.modality == Modality.ABSTRACT && !overridesDeclaration(kotlinClass, classMember)) || (classMember is PropertyDescriptor && classMember.modality == Modality.ABSTRACT && !overridesDeclaration(kotlinClass, classMember))
            }.mapNotNull { member ->
                when (member) {
                    is FunctionDescriptor -> createFunctionStub(member)
                    is PropertyDescriptor -> createVariableStub(member)
                    else -> null
                }
            }
        } else {
            null
        }
    }.flatten()

// interfaces are ClassDescriptors by default. When calling AbstractClass super methods, we get a ClassConstructorDescriptor    
private fun getClassDescriptor(descriptor: DeclarationDescriptor?): ClassDescriptor? = if (descriptor is ClassDescriptor) {
    descriptor
} else if (descriptor is ClassConstructorDescriptor) {
    descriptor.containingDeclaration
} else {
    null
}

private fun getSuperClassTypeProjections(file: CompiledFile, superType: KtSuperTypeListEntry): List<TypeProjection> = superType.typeReference?.typeElement?.children?.filter {
    it is KtTypeArgumentList
}?.flatMap {
    (it as KtTypeArgumentList).arguments
}?.mapNotNull {
    (file.referenceExpressionAtPoint(it?.startOffset ?: 0)?.second as? ClassDescriptor)?.defaultType?.asTypeProjection()
} ?: emptyList()

// Checks if the class overrides the given declaration
private fun overridesDeclaration(kotlinClass: KtClass, descriptor: FunctionDescriptor): Boolean =
    kotlinClass.declarations.any {
        if (it.name == descriptor.name.asString() && it.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            if (it is KtNamedFunction) {
                parametersMatch(it, descriptor)
            } else {
                true
            }
        } else {
            false
        }
    }

private fun overridesDeclaration(kotlinClass: KtClass, descriptor: PropertyDescriptor): Boolean =
    kotlinClass.declarations.any {
        it.name == descriptor.name.asString() && it.hasModifier(KtTokens.OVERRIDE_KEYWORD)
    }

// Checks if two functions have matching parameters
private fun parametersMatch(function: KtNamedFunction, functionDescriptor: FunctionDescriptor): Boolean {
    if (function.valueParameters.size == functionDescriptor.valueParameters.size) {
        for (index in 0 until function.valueParameters.size) {
            if (function.valueParameters[index].name != functionDescriptor.valueParameters[index].name.asString()) {
                return false
            } else if (function.valueParameters[index].typeReference?.typeName() != functionDescriptor.valueParameters[index].type.unwrappedType().toString()) {
                // Note: Since we treat Java overrides as non nullable by default, the above test will fail when the user has made the type nullable.
                // TODO: look into this
                return false
            }
        }

        if (function.typeParameters.size == functionDescriptor.typeParameters.size) {
            for (index in 0 until function.typeParameters.size) {
                if (function.typeParameters[index].variance != functionDescriptor.typeParameters[index].variance) {
                    return false
                }
            }
        }

        return true
    }

    return false
}

private fun KtTypeReference.typeName(): String? = this.name ?: this.typeElement?.children?.filter {
    it is KtSimpleNameExpression
}?.map {
    (it as KtSimpleNameExpression).getReferencedName()
}?.firstOrNull()

private fun createFunctionStub(function: FunctionDescriptor): String {
    val name = function.name
    val arguments = function.valueParameters.map { argument ->
        val argumentName = argument.name
        val argumentType = argument.type.unwrappedType()
                    
        "$argumentName: $argumentType"
    }.joinToString(", ")
    val returnType = function.returnType?.unwrappedType()?.toString()?.takeIf { "Unit" != it }
    
    return "override fun $name($arguments)${returnType?.let { ": $it" } ?: ""} { }"
}

private fun createVariableStub(variable: PropertyDescriptor): String {
    val variableType = variable.returnType?.unwrappedType()?.toString()?.takeIf { "Unit" != it }
    return "override val ${variable.name}${variableType?.let { ": $it" } ?: ""} = TODO(\"SET VALUE\")"
}

// about types: regular Kotlin types are marked T or T?, but types from Java are (T..T?) because nullability cannot be decided.
// Therefore we have to unpack in case we have the Java type. Fortunately, the Java types are not marked nullable, so we default to non nullable types. Let the user decide if they want nullable types instead. With this implementation Kotlin types also keeps their nullability
private fun KotlinType.unwrappedType(): KotlinType = this.unwrap().makeNullableAsSpecified(this.isMarkedNullable)

private fun getDeclarationPadding(file: CompiledFile, kotlinClass: KtClass): String {
    // If the class is not empty, the amount of padding is the same as the one in the last declaration of the class
    val paddingSize = if (kotlinClass.declarations.isNotEmpty()) {
        val lastFunctionStartOffset = kotlinClass.declarations.last().startOffset
        position(file.content, lastFunctionStartOffset).character
    } else {
        // Otherwise, we just use a default tab size in addition to any existing padding
        // on the class itself (note that the class could be inside another class, for example)
        position(file.content, kotlinClass.startOffset).character + DEFAULT_TAB_SIZE
    }

    return " ".repeat(paddingSize)
}

private fun getNewMembersStartPosition(file: CompiledFile, kotlinClass: KtClass): Position? =
    // If the class is not empty, the new member will be put right after the last declaration
    if (kotlinClass.declarations.isNotEmpty()) {
        val lastFunctionEndOffset = kotlinClass.declarations.last().endOffset
        position(file.content, lastFunctionEndOffset)
    } else { // Otherwise, the member is put at the beginning of the class
        val body = kotlinClass.body
        if (body != null) {
            position(file.content, body.startOffset + 1)
        } else {
            // function has no body. We have to create one. New position is right after entire kotlin class text (with space)
            val newPosCorrectLine = position(file.content, kotlinClass.startOffset + 1)
            newPosCorrectLine.character = (kotlinClass.text.length + 2)
            newPosCorrectLine
        }
    }

private fun KtClass.hasNoBody() = null == this.body
