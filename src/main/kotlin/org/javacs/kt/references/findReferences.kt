package org.javacs.kt.references

import org.javacs.kt.LOG
import org.javacs.kt.SourcePath
import org.javacs.kt.docs.preOrderTraversal
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions
import java.nio.file.Path

fun findReferences(file: Path, offset: Int, sources: SourcePath): Collection<KtReferenceExpression> {
    val recover = sources.recover(file, offset) ?: return emptyList()
    val element = recover.exprAt(0)?.parent as? KtNamedDeclaration ?: return emptyList()
    val declaration = recover.getDeclaration(element) ?: return emptyList()
    val maybes = sources.allSources().filter { mightReference(declaration, it) }
    LOG.info("Scanning ${maybes.size} files for references to ${element.fqName}")
    val recompile = sources.compileFiles(maybes)
    val references = recompile.getSliceContents(BindingContext.REFERENCE_TARGET)

    return references
            .filter { matchesReference(it.value, element) }
            .map { it.key }
}

private fun matchesReference(found: DeclarationDescriptor, search: KtNamedDeclaration): Boolean {
    if (found is ConstructorDescriptor && found.isPrimary)
        return search is KtClass && found.constructedClass.fqNameSafe == search.fqName
    else
        return found.findPsi() == search
}

private fun mightReference(target: DeclarationDescriptor, file: KtFile): Boolean =
        file.preOrderTraversal()
                .filterIsInstance<KtSimpleNameExpression>()
                .any { possibleNameMatch(target, it) }

private fun possibleNameMatch(target: DeclarationDescriptor, from: KtSimpleNameExpression): Boolean =
        when (from) {
            is KtOperationReferenceExpression -> from.operationSignTokenType in operatorNames(target)
            else -> name(target) == from.getReferencedNameAsName()
        }

private fun name(target: DeclarationDescriptor): Name =
        when (target) {
            is ClassConstructorDescriptor -> target.constructedClass.name
            else -> target.name
        }

private fun operatorNames(target: DeclarationDescriptor): List<KtSingleValueToken> {
    if (target is FunctionDescriptor && target.isOperator) {
        val name = target.name

        return when (name) {
            OperatorNameConventions.EQUALS -> listOf(KtTokens.EQEQ)
            OperatorNameConventions.COMPARE_TO -> listOf(KtTokens.GT, KtTokens.LT, KtTokens.LTEQ, KtTokens.GTEQ)
            else -> {
                val token = OperatorConventions.UNARY_OPERATION_NAMES.inverse()[name] ?:
                            OperatorConventions.BINARY_OPERATION_NAMES.inverse()[name] ?:
                            OperatorConventions.ASSIGNMENT_OPERATIONS.inverse()[name] ?:
                            OperatorConventions.BOOLEAN_OPERATIONS.inverse()[name]
                return listOfNotNull(token)
            }
        }
    }
    else return emptyList()
}
