package org.javacs.kt.completion

import org.javacs.kt.CompiledCode
import org.javacs.kt.position.findParent
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion
import org.jetbrains.kotlin.resolve.scopes.HierarchicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType

fun completions(code: CompiledCode): Sequence<DeclarationDescriptor> {
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
        val type = code.compiled.getType(dotParent.receiverExpression) ?: code.robustType(dotParent.receiverExpression)
                   ?: return emptySequence()
        val partial = matchIdentifier(dotParent.selectorExpression)

        return completeMembers(type, partial)
    }
    val idParent = expr.findParent<KtNameReferenceExpression>()
    if (idParent != null) {
        val scope = code.findScope(idParent) ?: return emptySequence()
        val partial = matchIdentifier(expr)

        return completeIdentifiers(scope, partial)
    }

    return emptySequence()
}

private fun matchIdentifier(exprAtCursor: KtExpression?): String {
    val select = exprAtCursor?.text ?: ""
    val word = Regex("[^()]+")

    return word.find(select)?.value ?: ""
}

fun memberOverloads(type: KotlinType, identifier: String): Sequence<CallableDescriptor> {
    val nameFilter = equalsIdentifier(identifier)

    return type.memberScope
            .getDescriptorsFiltered(Companion.CALLABLES, nameFilter).asSequence()
            .filter { nameFilter(it.name) }
            .filterIsInstance<CallableDescriptor>()
}

fun completeMembers(type: KotlinType, partialIdentifier: String): Sequence<DeclarationDescriptor> {
    val nameFilter = matchesPartialIdentifier(partialIdentifier)

    return doCompleteMembers(type, nameFilter)
}

private fun doCompleteMembers(type: KotlinType, nameFilter: (Name) -> Boolean): Sequence<DeclarationDescriptor> {
    return type.memberScope
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

private fun explodeConstructors(desc: DeclarationDescriptor): Sequence<DeclarationDescriptor> {
    return when (desc) {
        is ClassDescriptor ->
            desc.constructors.asSequence() + desc
        else ->
            sequenceOf(desc)
    }
}

private fun implicitMembers(scope: HierarchicalScope, nameFilter: (Name) -> Boolean): Sequence<DeclarationDescriptor> {
    if (scope !is LexicalScope) return emptySequence()
    val implicit = scope.implicitReceiver ?: return emptySequence()

    return doCompleteMembers(implicit.type, nameFilter)
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