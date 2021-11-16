package org.javacs.kt.references

import org.eclipse.lsp4j.Location
import org.javacs.kt.LOG
import org.javacs.kt.SourcePath
import org.javacs.kt.position.location
import org.javacs.kt.position.toURIString
import org.javacs.kt.util.emptyResult
import org.javacs.kt.util.findParent
import org.javacs.kt.util.preOrderTraversal
import org.javacs.kt.util.toPath
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

fun findReferences(file: Path, cursor: Int, sp: SourcePath): List<Location> {
    return doFindReferences(file, cursor, sp)
            .map { location(it) }
            .filterNotNull()
            .toList()
            .sortedWith(compareBy({ it.getUri() }, { it.getRange().getStart().getLine() }))
}

fun findReferences(declaration: KtNamedDeclaration, sp: SourcePath): List<Location> {
    return doFindReferences(declaration, sp)
        .map { location(it) }
        .filterNotNull()
        .toList()
        .sortedWith(compareBy({ it.getUri() }, { it.getRange().getStart().getLine() }))
}

private fun doFindReferences(file: Path, cursor: Int, sp: SourcePath): Collection<KtElement> {
    val recover = sp.currentVersion(file.toUri())
    val element = recover.elementAtPoint(cursor)?.findParent<KtNamedDeclaration>() ?: return emptyResult("No declaration at ${recover.describePosition(cursor)}")
    return doFindReferences(element, sp)
}

private fun doFindReferences(element: KtNamedDeclaration, sp: SourcePath): Collection<KtElement> {
    val recover = sp.currentVersion(element.containingFile.toPath().toUri())
    val declaration = recover.compile[BindingContext.DECLARATION_TO_DESCRIPTOR, element] ?: return emptyResult("Declaration ${element.fqName} has no descriptor")
    val maybes = possibleReferences(declaration, sp).map { it.toPath() }
    LOG.debug("Scanning {} files for references to {}", maybes.size, element.fqName)
    val recompile = sp.compileFiles(maybes.map(Path::toUri))

    return when {
        isComponent(declaration) -> findComponentReferences(element, recompile) + findNameReferences(element, recompile)
        isIterator(declaration) -> findIteratorReferences(element, recompile) + findNameReferences(element, recompile)
        isPropertyDelegate(declaration) -> findDelegateReferences(element, recompile) + findNameReferences(element, recompile)
        else -> findNameReferences(element, recompile)
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
private fun possibleReferences(declaration: DeclarationDescriptor, sp: SourcePath): Set<KtFile> {
    if (declaration is ClassConstructorDescriptor) {
        return possibleNameReferences(declaration.constructedClass.name, sp)
    }
    if (isComponent(declaration)) {
        return possibleComponentReferences(sp) + possibleNameReferences(declaration.name, sp)
    }
    if (isPropertyDelegate(declaration)) {
        return hasPropertyDelegates(sp) + possibleNameReferences(declaration.name, sp)
    }
    if (isGetSet(declaration)) {
        return possibleGetSets(sp) + possibleNameReferences(declaration.name, sp)
    }
    if (isIterator(declaration)) {
        return hasForLoops(sp) + possibleNameReferences(declaration.name, sp)
    }
    if (declaration is FunctionDescriptor && declaration.isOperator && declaration.name == OperatorNameConventions.INVOKE) {
        return possibleInvokeReferences(declaration, sp) + possibleNameReferences(declaration.name, sp)
    }
    if (declaration is FunctionDescriptor) {
        val operators = operatorNames(declaration.name)

        return possibleTokenReferences(operators, sp) + possibleNameReferences(declaration.name, sp)
    }
    return possibleNameReferences(declaration.name, sp)
}

private fun isPropertyDelegate(declaration: DeclarationDescriptor) =
        declaration is FunctionDescriptor &&
        declaration.isOperator &&
        (declaration.name == OperatorNameConventions.GET_VALUE || declaration.name == OperatorNameConventions.SET_VALUE)

private fun hasPropertyDelegates(sp: SourcePath): Set<KtFile> =
        sp.all().filter(::hasPropertyDelegate).toSet()

fun hasPropertyDelegate(source: KtFile): Boolean =
        source.preOrderTraversal().filterIsInstance<KtPropertyDelegate>().any()

private fun isIterator(declaration: DeclarationDescriptor) =
        declaration is FunctionDescriptor &&
        declaration.isOperator &&
        declaration.name == OperatorNameConventions.ITERATOR

private fun hasForLoops(sp: SourcePath): Set<KtFile> =
        sp.all().filter(::hasForLoop).toSet()

private fun hasForLoop(source: KtFile): Boolean =
        source.preOrderTraversal().filterIsInstance<KtForExpression>().any()

private fun isGetSet(declaration: DeclarationDescriptor) =
        declaration is FunctionDescriptor &&
        declaration.isOperator &&
        (declaration.name == OperatorNameConventions.GET || declaration.name == OperatorNameConventions.SET)

private fun possibleGetSets(sp: SourcePath): Set<KtFile> =
        sp.all().filter(::possibleGetSet).toSet()

private fun possibleGetSet(source: KtFile) =
        source.preOrderTraversal().filterIsInstance<KtArrayAccessExpression>().any()

private fun possibleInvokeReferences(declaration: FunctionDescriptor, sp: SourcePath) =
        sp.all().filter { possibleInvokeReference(declaration, it) }.toSet()

// TODO this is not very selective
private fun possibleInvokeReference(@Suppress("UNUSED_PARAMETER") declaration: FunctionDescriptor, source: KtFile): Boolean =
        source.preOrderTraversal().filterIsInstance<KtCallExpression>().any()

private fun isComponent(declaration: DeclarationDescriptor): Boolean =
        declaration is FunctionDescriptor &&
        declaration.isOperator &&
        OperatorNameConventions.COMPONENT_REGEX.matches(declaration.name.identifier)

private fun possibleComponentReferences(sp: SourcePath): Set<KtFile> =
        sp.all().filter { possibleComponentReference(it) }.toSet()

private fun possibleComponentReference(source: KtFile): Boolean =
        source.preOrderTraversal()
                .filterIsInstance<KtDestructuringDeclarationEntry>()
                .any()

private fun possibleTokenReferences(find: List<KtSingleValueToken>, sp: SourcePath): Set<KtFile> =
        sp.all().filter { possibleTokenReference(find, it) }.toSet()

private fun possibleTokenReference(find: List<KtSingleValueToken>, source: KtFile): Boolean =
        source.preOrderTraversal()
                .filterIsInstance<KtOperationReferenceExpression>()
                .any { it.operationSignTokenType in find }

private fun possibleNameReferences(declaration: Name, sp: SourcePath): Set<KtFile> =
        sp.all().filter { possibleNameReference(declaration, it) }.toSet()

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
