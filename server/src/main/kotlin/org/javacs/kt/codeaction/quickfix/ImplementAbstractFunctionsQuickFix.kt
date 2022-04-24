package org.javacs.kt.codeaction.quickfix

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledFile
import org.javacs.kt.index.SymbolIndex
import org.javacs.kt.position.offset
import org.javacs.kt.position.position
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.isAbstract
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

private const val DEFAULT_TAB_SIZE = 4

class ImplementAbstractFunctionsQuickFix : QuickFix {
    override fun compute(file: CompiledFile, index: SymbolIndex, range: Range, diagnostics: List<Diagnostic>): List<Either<Command, CodeAction>> {
        val diagnostic = findDiagnosticMatch(diagnostics, range)

        val startCursor = offset(file.content, range.start)
        val endCursor = offset(file.content, range.end)
        val kotlinDiagnostics = file.compile.diagnostics

        // If the client side and the server side diagnostics contain a valid diagnostic for this range.
        if (diagnostic != null && anyDiagnosticMatch(kotlinDiagnostics, startCursor, endCursor)) {
            // Get the class with the missing functions
            val kotlinClass = file.parseAtPoint(startCursor)
            if (kotlinClass is KtClass) {
                // Get the functions that need to be implemented
                val functionsToImplement = getAbstractFunctionStubs(file, kotlinClass)

                val uri = file.parse.toPath().toUri().toString()
                // Get the padding to be introduced before the function declarations
                val padding = getDeclarationPadding(file, kotlinClass)
                // Get the location where the new code will be placed
                val newFunctionStartPosition = getNewFunctionStartPosition(file, kotlinClass)

                val textEdits = functionsToImplement.map {
                    // We leave two new lines before the function is inserted
                    val newText = System.lineSeparator() + System.lineSeparator() + padding + it
                    TextEdit(Range(newFunctionStartPosition, newFunctionStartPosition), newText)
                }

                val codeAction = CodeAction()
                codeAction.edit = WorkspaceEdit(mapOf(uri to textEdits))
                codeAction.kind = CodeActionKind.QuickFix
                codeAction.title = "Implement abstract functions"
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

private fun getAbstractFunctionStubs(file: CompiledFile, kotlinClass: KtClass) =
    // For each of the super types used by this class
    kotlinClass.superTypeListEntries.mapNotNull {
        // Find the definition of this super type
        val referenceAtPoint = file.referenceExpressionAtPoint(it.startOffset)
        val descriptor = referenceAtPoint?.second
        val superClass = descriptor?.findPsi()

        val classDescriptor = descriptor as? ClassDescriptor
        val superClassTypeArguments = getSuperClassTypeArguments(file, it)        
        
        // If the super class is abstract or an interface
        if (superClass is KtClass && (superClass.isAbstract() || superClass.isInterface())) {
            // Get the abstract functions of this super type that are currently not implemented by this class
            val abstractFunctions = superClass.declarations.filter {
                declaration -> isAbstractFunction(declaration) && !overridesDeclaration(kotlinClass, declaration)
            }
            // Get stubs for each function
            abstractFunctions.map { function -> getFunctionStub(function as KtNamedFunction) }
        } else if(null != classDescriptor && (classDescriptor.kind.isInterface || classDescriptor.modality == Modality.ABSTRACT)) {
            // TODO: refactor and prettify
            classDescriptor.getMemberScope(superClassTypeArguments).getContributedDescriptors().filter { classMember ->
                classMember is FunctionDescriptor && classMember.modality == Modality.ABSTRACT && !overridesDeclaration(kotlinClass, classMember)
            }.map { function ->        
                createFunctionStub(function as FunctionDescriptor)
            }
        } else {
            null
        }
    }.flatten()

// TODO: better name?
private fun getSuperClassTypeArguments(file: CompiledFile, superType: KtSuperTypeListEntry): List<TypeProjection> = superType.typeReference?.typeElement?.children?.filter {
    it is KtTypeArgumentList
}?.flatMap {
    (it as KtTypeArgumentList).arguments
}?.mapNotNull {
    (file.referenceExpressionAtPoint(it?.startOffset ?: 0)?.second as? ClassDescriptor)?.defaultType?.asTypeProjection()
} ?: emptyList()
    
private fun isAbstractFunction(declaration: KtDeclaration): Boolean =
    declaration is KtNamedFunction && !declaration.hasBody()
        && (declaration.containingClass()?.isInterface() ?: false || declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD))

// Checks if the class overrides the given declaration
private fun overridesDeclaration(kotlinClass: KtClass, declaration: KtDeclaration): Boolean =
    kotlinClass.declarations.any {
        if (it.name == declaration.name && it.hasModifier(KtTokens.OVERRIDE_KEYWORD)) {
            if (it is KtNamedFunction && declaration is KtNamedFunction) {
                parametersMatch(it, declaration)
            } else {
                true
            }
        } else {
            false
        }
    }

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

// Checks if two functions have matching parameters
private fun parametersMatch(function: KtNamedFunction, functionDeclaration: KtNamedFunction): Boolean {
    if (function.valueParameters.size == functionDeclaration.valueParameters.size) {
        for (index in 0 until function.valueParameters.size) {
            if (function.valueParameters[index].name != functionDeclaration.valueParameters[index].name) {
                return false
            } else if (function.valueParameters[index].typeReference?.name != functionDeclaration.valueParameters[index].typeReference?.name) {
                return false
            }
        }

        if (function.typeParameters.size == functionDeclaration.typeParameters.size) {
            for (index in 0 until function.typeParameters.size) {
                if (function.typeParameters[index].variance != functionDeclaration.typeParameters[index].variance) {
                    return false
                }
            }
        }

        return true
    }

    return false
}

private fun parametersMatch(function: KtNamedFunction, functionDescriptor: FunctionDescriptor): Boolean {
    if (function.valueParameters.size == functionDescriptor.valueParameters.size) {
        for (index in 0 until function.valueParameters.size) {
            if (function.valueParameters[index].name != functionDescriptor.valueParameters[index].name.asString()) {
                return false
            } else if (function.valueParameters[index].typeReference?.name != functionDescriptor.valueParameters[index].type.toString()) {
                // TODO: here we have exactly the old issue of type as above
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

private fun getFunctionStub(function: KtNamedFunction): String =
    "override fun" + function.text.substringAfter("fun") + " { }"

private fun createFunctionStub(function: FunctionDescriptor): String {
    // TODO: clean
    val name = function.name
    val arguments = function.valueParameters.map { argument ->
        val argumentName = argument.name
        // TODO: how to check if we actually should unwrap and make non-nullable?
        val argumentType = argument.type.unwrap().makeNullableAsSpecified(false)
                    
        "$argumentName: $argumentType"
    }.joinToString(", ")
    val returnType = function.returnType?.toString()
    
    return "override fun $name($arguments)${returnType?.takeIf { "Unit" != it }?.let { ": $it" } ?: ""} { }"
}

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

private fun getNewFunctionStartPosition(file: CompiledFile, kotlinClass: KtClass): Position? =
    // If the class is not empty, the new function will be put right after the last declaration
    if (kotlinClass.declarations.isNotEmpty()) {
        val lastFunctionEndOffset = kotlinClass.declarations.last().endOffset
        position(file.content, lastFunctionEndOffset)
    } else { // Otherwise, the function is put at the beginning of the class
        val body = kotlinClass.body
        if (body != null) {
            position(file.content, body.startOffset + 1)
        } else {
            null
        }
    }
