package org.javacs.kt.overridemembers

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.javacs.kt.CompiledFile
import org.javacs.kt.util.toPath
import org.javacs.kt.position.position
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

// TODO: see where this should ideally be placed
private const val DEFAULT_TAB_SIZE = 4

fun listOverridableMembers(file: CompiledFile, cursor: Int): List<CodeAction> {
    val kotlinClass = file.parseAtPoint(cursor)

    if (kotlinClass is KtClass) {
        return createOverrideAlternatives(file, kotlinClass)
    }

    return emptyList()
}

private fun createOverrideAlternatives(file: CompiledFile, kotlinClass: KtClass): List<CodeAction> {
    // Get the functions that need to be implemented
    val membersToImplement = getUnimplementedMembersStubs(file, kotlinClass)

    val uri = file.parse.toPath().toUri().toString()

    // Get the padding to be introduced before the member declarations
    val padding = getDeclarationPadding(file, kotlinClass)

    // Get the location where the new code will be placed
    val newMembersStartPosition = getNewMembersStartPosition(file, kotlinClass)
    
    // loop through the memberstoimplement and create code actions
    return membersToImplement.map { member ->
        val newText = System.lineSeparator() + System.lineSeparator() + padding + member
        val textEdit = TextEdit(Range(newMembersStartPosition, newMembersStartPosition), newText)

        val codeAction = CodeAction()
        codeAction.edit = WorkspaceEdit(mapOf(uri to listOf(textEdit)))
        codeAction.title = member

        codeAction
    }
}

// TODO: any way can repeat less code between this and the getAbstractMembersStubs in the ImplementAbstractMembersQuickfix?
private fun getUnimplementedMembersStubs(file: CompiledFile, kotlinClass: KtClass): List<String> =
    // For each of the super types used by this class
    // TODO: does not seem to handle the implicit Any and Object super types that well. Need to find out if that is easily solvable. Finds the methods from them if any super class or interface is present
    kotlinClass
        .superTypeListEntries
        .mapNotNull {
            // Find the definition of this super type
            val referenceAtPoint = file.referenceExpressionAtPoint(it.startOffset)
            val descriptor = referenceAtPoint?.second
            val classDescriptor = getClassDescriptor(descriptor)

            // If the super class is abstract, interface or just plain open
            if (null != classDescriptor && classDescriptor.canBeExtended()) {
                val superClassTypeArguments = getSuperClassTypeProjections(file, it)
                classDescriptor
                    .getMemberScope(superClassTypeArguments)
                    .getContributedDescriptors()
                    .filter { classMember ->
                        classMember is MemberDescriptor &&
                         classMember.canBeOverridden() &&
                         !overridesDeclaration(kotlinClass, classMember)
                    }
                    .mapNotNull { member ->
                        when (member) {
                            is FunctionDescriptor -> createFunctionStub(member)
                            is PropertyDescriptor -> createVariableStub(member)
                            else -> null
                        }
                    }
            } else {
                null
            }
        }
        .flatten()

private fun ClassDescriptor.canBeExtended() = this.kind.isInterface ||
    this.modality == Modality.ABSTRACT ||
    this.modality == Modality.OPEN
            
private fun MemberDescriptor.canBeOverridden() = (Modality.ABSTRACT == this.modality || Modality.OPEN == this.modality) && Modality.FINAL != this.modality && this.visibility != DescriptorVisibilities.PRIVATE && this.visibility != DescriptorVisibilities.PROTECTED

// interfaces are ClassDescriptors by default. When calling AbstractClass super methods, we get a ClassConstructorDescriptor
fun getClassDescriptor(descriptor: DeclarationDescriptor?): ClassDescriptor? =
        if (descriptor is ClassDescriptor) {
            descriptor
        } else if (descriptor is ClassConstructorDescriptor) {
            descriptor.containingDeclaration
        } else {
            null
        }

fun getSuperClassTypeProjections(
        file: CompiledFile,
        superType: KtSuperTypeListEntry
): List<TypeProjection> =
        superType
                .typeReference
                ?.typeElement
                ?.children
                ?.filter { it is KtTypeArgumentList }
                ?.flatMap { (it as KtTypeArgumentList).arguments }
                ?.mapNotNull {
                    (file.referenceExpressionAtPoint(it?.startOffset ?: 0)?.second as?
                                    ClassDescriptor)
                            ?.defaultType?.asTypeProjection()
                }
                ?: emptyList()

// Checks if the class overrides the given declaration
fun overridesDeclaration(kotlinClass: KtClass, descriptor: MemberDescriptor): Boolean =
    when (descriptor) {
        is FunctionDescriptor -> kotlinClass.declarations.any {
            it.name == descriptor.name.asString()
            && it.hasModifier(KtTokens.OVERRIDE_KEYWORD)
            && ((it as? KtNamedFunction)?.let { parametersMatch(it, descriptor) } ?: true)
        }
        is PropertyDescriptor -> kotlinClass.declarations.any {
            it.name == descriptor.name.asString() && it.hasModifier(KtTokens.OVERRIDE_KEYWORD)
        }
        else -> false
    }

// Checks if two functions have matching parameters
private fun parametersMatch(
        function: KtNamedFunction,
        functionDescriptor: FunctionDescriptor
): Boolean {
    if (function.valueParameters.size == functionDescriptor.valueParameters.size) {
        for (index in 0 until function.valueParameters.size) {
            if (function.valueParameters[index].name !=
                    functionDescriptor.valueParameters[index].name.asString()
            ) {
                return false
            } else if (function.valueParameters[index].typeReference?.typeName() !=
                            functionDescriptor.valueParameters[index]
                                    .type
                                    .unwrappedType()
                                    .toString() && function.valueParameters[index].typeReference?.typeName() != null
            ) {
                // Any and Any? seems to be null for Kt* psi objects for some reason? At least for equals
                // TODO: look further into this
                
                // Note: Since we treat Java overrides as non nullable by default, the above test
                // will fail when the user has made the type nullable.
                // TODO: look into this
                return false
            }
        }

        if (function.typeParameters.size == functionDescriptor.typeParameters.size) {
            for (index in 0 until function.typeParameters.size) {
                if (function.typeParameters[index].variance !=
                        functionDescriptor.typeParameters[index].variance
                ) {
                    return false
                }
            }
        }

        return true
    }

    return false
}

private fun KtTypeReference.typeName(): String? =
        this.name
                ?: this.typeElement
                        ?.children
                        ?.filter { it is KtSimpleNameExpression }
                        ?.map { (it as KtSimpleNameExpression).getReferencedName() }
                        ?.firstOrNull()

fun createFunctionStub(function: FunctionDescriptor): String {
    val name = function.name
    val arguments =
            function.valueParameters
                    .map { argument ->
                        val argumentName = argument.name
                        val argumentType = argument.type.unwrappedType()

                        "$argumentName: $argumentType"
                    }
                    .joinToString(", ")
    val returnType = function.returnType?.unwrappedType()?.toString()?.takeIf { "Unit" != it }

    return "override fun $name($arguments)${returnType?.let { ": $it" } ?: ""} { }"
}

fun createVariableStub(variable: PropertyDescriptor): String {
    val variableType = variable.returnType?.unwrappedType()?.toString()?.takeIf { "Unit" != it }
    return "override val ${variable.name}${variableType?.let { ": $it" } ?: ""} = TODO(\"SET VALUE\")"
}

// about types: regular Kotlin types are marked T or T?, but types from Java are (T..T?) because
// nullability cannot be decided.
// Therefore we have to unpack in case we have the Java type. Fortunately, the Java types are not
// marked nullable, so we default to non nullable types. Let the user decide if they want nullable
// types instead. With this implementation Kotlin types also keeps their nullability
private fun KotlinType.unwrappedType(): KotlinType =
        this.unwrap().makeNullableAsSpecified(this.isMarkedNullable)

fun getDeclarationPadding(file: CompiledFile, kotlinClass: KtClass): String {
    // If the class is not empty, the amount of padding is the same as the one in the last
    // declaration of the class
    val paddingSize =
            if (kotlinClass.declarations.isNotEmpty()) {
                val lastFunctionStartOffset = kotlinClass.declarations.last().startOffset
                position(file.content, lastFunctionStartOffset).character
            } else {
                // Otherwise, we just use a default tab size in addition to any existing padding
                // on the class itself (note that the class could be inside another class, for
                // example)
                position(file.content, kotlinClass.startOffset).character + DEFAULT_TAB_SIZE
            }

    return " ".repeat(paddingSize)
}

fun getNewMembersStartPosition(file: CompiledFile, kotlinClass: KtClass): Position? =
        // If the class is not empty, the new member will be put right after the last declaration
        if (kotlinClass.declarations.isNotEmpty()) {
            val lastFunctionEndOffset = kotlinClass.declarations.last().endOffset
            position(file.content, lastFunctionEndOffset)
        } else { // Otherwise, the member is put at the beginning of the class
            val body = kotlinClass.body
            if (body != null) {
                position(file.content, body.startOffset + 1)
            } else {
                // function has no body. We have to create one. New position is right after entire
                // kotlin class text (with space)
                val newPosCorrectLine = position(file.content, kotlinClass.startOffset + 1)
                newPosCorrectLine.character = (kotlinClass.text.length + 2)
                newPosCorrectLine
            }
        }

fun KtClass.hasNoBody() = null == this.body
