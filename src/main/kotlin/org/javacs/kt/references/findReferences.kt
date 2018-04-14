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
    val recover = sources.compiledCode(file, offset)
    val element = recover.exprAt(0)?.parent as? KtNamedDeclaration ?: return emptyList()
    val declaration = recover.getDeclaration(element) ?: return emptyList()
    val maybes = possibleReferences(declaration, sources)
    LOG.info("Scanning ${maybes.size} files for references to ${element.fqName}")
    val recompile = sources.compileFiles(maybes)

    if (isComponent(declaration)) {
        return findComponentReferences(element, recompile) + findNameReferences(element, recompile)
    }
    else if (isIterator(declaration)) {
        return findIteratorReferences(element, recompile) + findNameReferences(element, recompile)
    }
    else if (isPropertyDelegate(declaration)) {
        return findDelegateReferences(element, recompile) + findNameReferences(element, recompile)
    }
    else {
        return findNameReferences(element, recompile)
    }
}

private fun findNameReferences(element: KtNamedDeclaration, recompile: BindingContext): List<KtReferenceExpression> {
    val references = recompile.getSliceContents(BindingContext.REFERENCE_TARGET)

    return references.filter { matchesReference(it.value, element) }.map { it.key }
}

private fun findDelegateReferences(element: KtNamedDeclaration, recompile: BindingContext): List<KtElement> {
    val references = recompile.getSliceContents(BindingContext.DELEGATED_PROPERTY_RESOLVED_CALL)

    return references
            .filter { matchesReference(it.value.candidateDescriptor, element) }
            .map { it.value.call.callElement }
}

private fun findIteratorReferences(element: KtNamedDeclaration, recompile: BindingContext): List<KtElement> {
    val references = recompile.getSliceContents(BindingContext.LOOP_RANGE_ITERATOR_RESOLVED_CALL)

    return references
            .filter { matchesReference( it.value.candidateDescriptor, element) }
            .map { it.value.call.callElement }
}

private fun findComponentReferences(element: KtNamedDeclaration, recompile: BindingContext): List<KtElement> {
    val references = recompile.getSliceContents(BindingContext.COMPONENT_RESOLVED_CALL)

    return references
            .filter { matchesReference(it.value.candidateDescriptor, element) }
            .map { it.value.call.callElement }
}

// TODO use imports to limit search
private fun possibleReferences(declaration: DeclarationDescriptor, sources: SourcePath): Set<KtFile> {
    if (declaration is ClassConstructorDescriptor) {
        return possibleNameReferences(declaration.constructedClass.name, sources)
    }
    if (isComponent(declaration)) {
        return possibleComponentReferences(sources) + possibleNameReferences(declaration.name, sources)
    }
    if (isPropertyDelegate(declaration)) {
        return hasPropertyDelegates(sources) + possibleNameReferences(declaration.name, sources)
    }
    if (isGetSet(declaration)) {
        return possibleGetSets(sources) + possibleNameReferences(declaration.name, sources)
    }
    if (isIterator(declaration)) {
        return hasForLoops(sources) + possibleNameReferences(declaration.name, sources)
    }
    if (declaration is FunctionDescriptor && declaration.isOperator && declaration.name == OperatorNameConventions.INVOKE) {
        return possibleInvokeReferences(declaration, sources) + possibleNameReferences(declaration.name, sources)
    }
    if (declaration is FunctionDescriptor) {
        val operators = operatorNames(declaration.name)

        return possibleTokenReferences(operators, sources) + possibleNameReferences(declaration.name, sources)
    }
    return possibleNameReferences(declaration.name, sources)
}

private fun isPropertyDelegate(declaration: DeclarationDescriptor) =
        declaration is FunctionDescriptor &&
        declaration.isOperator &&
        (declaration.name == OperatorNameConventions.GET_VALUE || declaration.name == OperatorNameConventions.SET_VALUE)

private fun hasPropertyDelegates(sources: SourcePath): Set<KtFile> =
        sources.all().filter(::hasPropertyDelegate).toSet()

fun hasPropertyDelegate(source: KtFile): Boolean =
        source.preOrderTraversal().filterIsInstance<KtPropertyDelegate>().any()

private fun isIterator(declaration: DeclarationDescriptor) =
        declaration is FunctionDescriptor &&
        declaration.isOperator &&
        declaration.name == OperatorNameConventions.ITERATOR

private fun hasForLoops(sources: SourcePath): Set<KtFile> =
        sources.all().filter(::hasForLoop).toSet()

private fun hasForLoop(source: KtFile): Boolean =
        source.preOrderTraversal().filterIsInstance<KtForExpression>().any()

private fun isGetSet(declaration: DeclarationDescriptor) =
        declaration is FunctionDescriptor &&
        declaration.isOperator &&
        (declaration.name == OperatorNameConventions.GET || declaration.name == OperatorNameConventions.SET)

private fun possibleGetSets(sources: SourcePath): Set<KtFile> =
        sources.all().filter(::possibleGetSet).toSet()

private fun possibleGetSet(source: KtFile) =
        source.preOrderTraversal().filterIsInstance<KtArrayAccessExpression>().any()

private fun possibleInvokeReferences(declaration: FunctionDescriptor, sources: SourcePath) =
        sources.all().filter { possibleInvokeReference(declaration, it) }.toSet()

// TODO this is not very selective
private fun possibleInvokeReference(declaration: FunctionDescriptor, source: KtFile): Boolean =
        source.preOrderTraversal().filterIsInstance<KtCallExpression>().any()

private fun isComponent(declaration: DeclarationDescriptor): Boolean =
        declaration is FunctionDescriptor &&
        declaration.isOperator &&
        OperatorNameConventions.COMPONENT_REGEX.matches(declaration.name.identifier)

private fun possibleComponentReferences(sources: SourcePath): Set<KtFile> =
        sources.all().filter { possibleComponentReference(it) }.toSet()

private fun possibleComponentReference(source: KtFile): Boolean =
        source.preOrderTraversal()
                .filterIsInstance<KtDestructuringDeclarationEntry>()
                .any()

private fun possibleTokenReferences(find: List<KtSingleValueToken>, sources: SourcePath): Set<KtFile> =
        sources.all().filter { possibleTokenReference(find, it) }.toSet()

private fun possibleTokenReference(find: List<KtSingleValueToken>, source: KtFile): Boolean =
        source.preOrderTraversal()
                .filterIsInstance<KtOperationReferenceExpression>()
                .any { it.operationSignTokenType in find }

private fun possibleNameReferences(declaration: Name, sources: SourcePath): Set<KtFile> =
        sources.all().filter { possibleNameReference(declaration, it) }.toSet()

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
