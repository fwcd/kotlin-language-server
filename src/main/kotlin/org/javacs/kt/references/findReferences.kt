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

fun findReferences(file: Path, offset: Int, sources: SourcePath): Collection<KtElement> {
    val recover = sources.recover(file, offset) ?: return emptyList()
    val element = recover.exprAt(0)?.parent as? KtNamedDeclaration ?: return emptyList()
    val declaration = recover.getDeclaration(element) ?: return emptyList()
    val maybes = findPossibleReferences(declaration, sources)
    LOG.info("Scanning ${maybes.size} files for references to ${element.fqName}")
    val recompile = sources.compileFiles(maybes)

    if (isComponent(declaration)) {
        val references = recompile.getSliceContents(BindingContext.COMPONENT_RESOLVED_CALL)

        return references
                .filter { matchesReference(it.value.candidateDescriptor, element) }
                .map { it.value.call.callElement }
    }
    else {
        val references = recompile.getSliceContents(BindingContext.REFERENCE_TARGET)

        return references
                .filter { matchesReference(it.value, element) }
                .map { it.key }
    }
}

private fun findPossibleReferences(declaration: DeclarationDescriptor, sources: SourcePath): Set<KtFile> {
    if (declaration is ClassConstructorDescriptor) {
        return possibleNameReferences(declaration.constructedClass.name, sources)
    }
    if (isComponent(declaration)) {
        return componentReferences(sources) + possibleNameReferences(declaration.name, sources)
    }
    if (declaration is FunctionDescriptor) {
        val operators = operatorNames(declaration.name)

        return tokenReferences(operators, sources) + possibleNameReferences(declaration.name, sources)
    }
    return possibleNameReferences(declaration.name, sources)
}

private fun isComponent(declaration: DeclarationDescriptor): Boolean =
        declaration is FunctionDescriptor &&
        declaration.isOperator &&
        OperatorNameConventions.COMPONENT_REGEX.matches(declaration.name.identifier)

private fun componentReferences(sources: SourcePath): Set<KtFile> =
        sources.allSources().values.filter { componentReference(it) }.toSet()

private fun componentReference(source: KtFile): Boolean =
        source.preOrderTraversal()
                .filterIsInstance<KtDestructuringDeclarationEntry>()
                .any()

private fun tokenReferences(find: List<KtSingleValueToken>, sources: SourcePath): Set<KtFile> =
        sources.allSources().values.filter { tokenReference(find, it) }.toSet()

private fun tokenReference(find: List<KtSingleValueToken>, source: KtFile): Boolean =
        source.preOrderTraversal()
                .filterIsInstance<KtOperationReferenceExpression>()
                .any { it.operationSignTokenType in find }

private fun possibleNameReferences(declaration: Name, sources: SourcePath): Set<KtFile> =
        sources.allSources().values.filter { possibleNameReference(declaration, it) }.toSet()

private fun possibleNameReference(declaration: Name, source: KtFile): Boolean =
        source.preOrderTraversal()
                .filterIsInstance<KtSimpleNameExpression>()
                .any { it.getReferencedNameAsName() == declaration }

private fun matchesReference(found: DeclarationDescriptor, search: KtNamedDeclaration): Boolean {
    if (found is ConstructorDescriptor && found.isPrimary)
        return search is KtClass && found.constructedClass.fqNameSafe == search.fqName
    else
        return found.findPsi() == search
}

private fun operatorNames(name: Name): List<KtSingleValueToken> =
        when (name) {
            OperatorNameConventions.EQUALS -> listOf(KtTokens.EQEQ)
            OperatorNameConventions.COMPARE_TO -> listOf(KtTokens.GT, KtTokens.LT, KtTokens.LTEQ, KtTokens.GTEQ)
            else -> {
                val token = OperatorConventions.UNARY_OPERATION_NAMES.inverse()[name] ?:
                            OperatorConventions.BINARY_OPERATION_NAMES.inverse()[name] ?:
                            OperatorConventions.ASSIGNMENT_OPERATIONS.inverse()[name] ?:
                            OperatorConventions.BOOLEAN_OPERATIONS.inverse()[name]
                listOfNotNull(token)
            }
        }
