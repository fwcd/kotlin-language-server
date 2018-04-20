package org.javacs.kt.completion

import com.google.common.cache.CacheBuilder
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.javacs.kt.CompiledCode
import org.javacs.kt.LOG
import org.javacs.kt.util.findParent
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.bindingContextUtil.getReferenceTargets
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.scopes.*
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import java.util.concurrent.TimeUnit

private const val MAX_COMPLETION_ITEMS = 50

fun completions(code: CompiledCode): CompletionList {
    val completions = doCompletions(code)
    val visible = completions.filter(isVisible(code))
    val list = visible.map(::completionItem).take(MAX_COMPLETION_ITEMS).toList()
    val isIncomplete = list.size == MAX_COMPLETION_ITEMS
    return CompletionList(isIncomplete, list)
}

private fun completionItem(declaration: DeclarationDescriptor): CompletionItem =
        declaration.accept(RenderCompletionItem(), null)

private fun doCompletions(code: CompiledCode): Sequence<DeclarationDescriptor> {
    val psi = code.parsed.findElementAt(code.offset(-1)) ?: return emptySequence()
    val expr = psi.findParent<KtExpression>() ?: return emptySequence()
    val typeParent = expr.findParent<KtTypeElement>()
    if (typeParent != null) {
        val scope = code.findScope(expr) ?: return emptySequence()
        val partial = matchIdentifier(expr)

        return completeTypes(scope, partial)
    }
    val dotParent = expr.findParent<KtDotQualifiedExpression>()
    if (dotParent != null) {
        val receiver = dotParent.receiverExpression
        val scope = memberScope(receiver, code) ?: return cantFindMemberScope(receiver)
        val partial = matchIdentifier(dotParent)

        return completeMembers(scope, partial)
    }
    val idParent = expr.findParent<KtNameReferenceExpression>()
    if (idParent != null) {
        val scope = code.findScope(idParent) ?: return emptySequence()
        val partial = matchIdentifier(expr)

        return completeIdentifiers(scope, partial)
    }

    return emptySequence()
}

private fun memberScope(expr: KtExpression, code: CompiledCode): MemberScope? =
        typeScope(expr, code) ?: staticScope(expr, code.compiled)

private fun typeScope(expr: KtExpression, code: CompiledCode): MemberScope? =
        robustType(expr, code)?.memberScope

private fun robustType(expr: KtExpression, code: CompiledCode): KotlinType? =
        code.compiled.getType(expr) ?: code.robustType(expr)

private fun staticScope(expr: KtExpression, context: BindingContext): MemberScope? =
        expr.getReferenceTargets(context).filterIsInstance<ClassDescriptor>().map { it.staticScope }.firstOrNull()

private fun <T> cantFindMemberScope(expr: KtExpression): Sequence<T> {
    LOG.info("Can't find member scope for ${expr.text}")

    return emptySequence()
}

private val FIND_ID = Regex("\\.(\\w+)")

private fun matchIdentifier(dotExpr: KtExpression): String {
    val match = FIND_ID.find(dotExpr.text) ?: return ""
    val word = match.groups[1] ?: return ""
    return word.value
}

fun memberOverloads(type: KotlinType, identifier: String): Sequence<CallableDescriptor> {
    val nameFilter = equalsIdentifier(identifier)

    return type.memberScope
            .getDescriptorsFiltered(Companion.CALLABLES, nameFilter).asSequence()
            .filter { nameFilter(it.name) }
            .filterIsInstance<CallableDescriptor>()
}

fun completeMembers(scope: MemberScope, partialIdentifier: String): Sequence<DeclarationDescriptor> {
    val nameFilter = matchesPartialIdentifier(partialIdentifier)

    return doCompleteMembers(scope, nameFilter)
}

private fun doCompleteMembers(scope: MemberScope, nameFilter: (Name) -> Boolean): Sequence<DeclarationDescriptor> {
    return scope
            .getDescriptorsFiltered(DescriptorKindFilter.ALL, nameFilter).asSequence()
            .filter { nameFilter(it.name) }
}

fun completeTypes(scope: LexicalScope, partialIdentifier: String): Sequence<DeclarationDescriptor> {
    val kindsFilter = DescriptorKindFilter(DescriptorKindFilter.NON_SINGLETON_CLASSIFIERS_MASK or DescriptorKindFilter.TYPE_ALIASES_MASK)
    val nameFilter = matchesPartialIdentifier(partialIdentifier)

    return scope.parentsWithSelf
            .flatMap { it.getContributedDescriptors(kindsFilter, nameFilter).asSequence() }
            .filter { nameFilter(it.name) }
}

fun identifierOverloads(scope: LexicalScope, identifier: String): Sequence<CallableDescriptor> {
    val nameFilter = equalsIdentifier(identifier)

    return allIdentifiers(scope, nameFilter)
            .filterIsInstance<CallableDescriptor>()
}

fun completeIdentifiers(scope: LexicalScope, partialIdentifier: String): Sequence<DeclarationDescriptor> {
    val nameFilter = matchesPartialIdentifier(partialIdentifier)

    return allIdentifiers(scope, nameFilter)
}

private fun allIdentifiers(scope: LexicalScope, nameFilter: (Name) -> Boolean): Sequence<DeclarationDescriptor> {
    val matchesName = scope.parentsWithSelf
            .flatMap { scopeIdentifiers(it, nameFilter) }
            .filter { nameFilter(it.name) }

    return matchesName.flatMap(::explodeConstructors)
}

private fun scopeIdentifiers(scope: HierarchicalScope, nameFilter: (Name) -> Boolean): Sequence<DeclarationDescriptor> {
    val locals = scope.getContributedDescriptors(DescriptorKindFilter.ALL, nameFilter).asSequence()
    val members = implicitMembers(scope, nameFilter)

    return locals + members
}

private fun explodeConstructors(declaration: DeclarationDescriptor): Sequence<DeclarationDescriptor> {
    return when (declaration) {
        is ClassDescriptor ->
            declaration.constructors.asSequence() + declaration
        else ->
            sequenceOf(declaration)
    }
}

private fun implicitMembers(scope: HierarchicalScope, nameFilter: (Name) -> Boolean): Sequence<DeclarationDescriptor> {
    if (scope !is LexicalScope) return emptySequence()
    val implicit = scope.implicitReceiver ?: return emptySequence()

    return doCompleteMembers(implicit.type.memberScope, nameFilter)
}

private fun equalsIdentifier(identifier: String): (Name) -> Boolean {
    return { it.identifier == identifier }
}

private fun matchesPartialIdentifier(partialIdentifier: String): (Name) -> Boolean {
    return {
        containsCharactersInOrder(it.identifier, partialIdentifier, false)
    }
}

fun containsCharactersInOrder(
        candidate: CharSequence, pattern: CharSequence, caseSensitive: Boolean): Boolean {
    var iCandidate = 0
    var iPattern = 0

    while (iCandidate < candidate.length && iPattern < pattern.length) {
        var patternChar = pattern[iPattern]
        var testChar = candidate[iCandidate]

        if (!caseSensitive) {
            patternChar = Character.toLowerCase(patternChar)
            testChar = Character.toLowerCase(testChar)
        }

        if (patternChar == testChar) {
            iPattern++
            iCandidate++
        } else iCandidate++
    }

    return iPattern == pattern.length
}

private fun isVisible(code: CompiledCode): (DeclarationDescriptor) -> Boolean {
    val expr = code.parsed.findElementAt(code.offset(0)) ?: return noExpressionAtCursor(code)
    val from = expr.parentsWithSelf
                       .mapNotNull { code.compiled[BindingContext.DECLARATION_TO_DESCRIPTOR, it] }
                       .firstOrNull() ?: return noDeclarationAroundCursor(code)

    fun check(target: DeclarationDescriptor): Boolean {
        val visible = isDeclarationVisible(target, from)

        if (!visible) logHidden(target, from)

        return visible
    }

    return ::check
}

// We can't use the implementations in Visibilities because they don't work with our type of incremental compilation
// Instead, we implement our own "liberal" visibility checker that defaults to visible when in doubt
private fun isDeclarationVisible(target: DeclarationDescriptor, from: DeclarationDescriptor): Boolean =
    target.parentsWithSelf
            .filterIsInstance<DeclarationDescriptorWithVisibility>()
            .none { isNotVisible(it, from) }

private fun isNotVisible(target: DeclarationDescriptorWithVisibility, from: DeclarationDescriptor): Boolean {
    when (target.visibility) {
        Visibilities.PRIVATE, Visibilities.PRIVATE_TO_THIS -> {
            if (DescriptorUtils.isTopLevelDeclaration(target))
                return !sameFile(target, from)
            else
                return !sameParent(target, from)
        }
        Visibilities.PROTECTED -> {
            return !subclassParent(target, from)
        }
        else -> return false
    }
}

private fun sameFile(target: DeclarationDescriptor, from: DeclarationDescriptor): Boolean {
    val targetFile = DescriptorUtils.getContainingSourceFile(target)
    val fromFile = DescriptorUtils.getContainingSourceFile(from)

    if (targetFile == SourceFile.NO_SOURCE_FILE || fromFile == SourceFile.NO_SOURCE_FILE) return true
    else return targetFile.name == fromFile.name
}

private fun sameParent(target: DeclarationDescriptor, from: DeclarationDescriptor): Boolean {
    val targetParent = target.parentsWithSelf.mapNotNull(::isParentClass).firstOrNull() ?: return true
    val fromParents = from.parentsWithSelf.mapNotNull(::isParentClass).toList()

    if (fromParents.isEmpty()) return true
    else return fromParents.any { it.fqNameSafe == targetParent.fqNameSafe }
}

private fun subclassParent(target: DeclarationDescriptor, from: DeclarationDescriptor): Boolean {
    val targetParent = target.parentsWithSelf.mapNotNull(::isParentClass).firstOrNull() ?: return true
    val fromParents = from.parentsWithSelf.mapNotNull(::isParentClass).toList()

    if (fromParents.isEmpty()) return true
    else return fromParents.any { DescriptorUtils.isSubclass(it, targetParent) }
}

private fun isParentClass(declaration: DeclarationDescriptor): ClassDescriptor? =
    if (declaration is ClassDescriptor && !DescriptorUtils.isCompanionObject(declaration))
        declaration
    else null

private val loggedHidden = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build<Pair<Name, Name>, Unit>()

private fun logHidden(target: DeclarationDescriptor, from: DeclarationDescriptor) {
    val key = Pair(from.name, target.name)

    loggedHidden.get(key, { doLogHidden(target, from )})
}

private fun doLogHidden(target: DeclarationDescriptor, from: DeclarationDescriptor) {
    LOG.info("Hiding ${describeDeclaration(target)} because it's not visible from ${describeDeclaration(from)}")
}

private fun describeDeclaration(declaration: DeclarationDescriptor): String {
    val file = declaration.findPsi()?.containingFile?.toPath()?.fileName?.toString() ?: "<unknown-file>"
    val container = declaration.containingDeclaration?.name?.toString() ?: "<top-level>"

    return "($file $container.${declaration.name})"
}

private fun noExpressionAtCursor(code: CompiledCode): (DeclarationDescriptor) -> Boolean {
    LOG.info("Can't determine visibility because there is no expression at the cursor ${code.describePosition(0)}")

    return { _ -> true }
}

private fun noDeclarationAroundCursor(code: CompiledCode): (DeclarationDescriptor) -> Boolean {
    LOG.info("Can't determine visibility because there is no declaration around the cursor ${code.describePosition(0)}")

    return { _ -> true }
}