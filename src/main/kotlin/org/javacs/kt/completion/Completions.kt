package org.javacs.kt.completion

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.HierarchicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType

fun completeMembers(type: KotlinType, partialIdentifier: String): Collection<DeclarationDescriptor> {
    val nameFilter = matchesPartialIdentifier(partialIdentifier)

    return type.memberScope.getDescriptorsFiltered(DescriptorKindFilter.ALL, nameFilter)
}

fun completeIdentifiers(scope: LexicalScope, partialIdentifier: String): Sequence<DeclarationDescriptor> {
    val nameFilter = matchesPartialIdentifier(partialIdentifier)

    return scope.parentsWithSelf.flatMap {
        val locals = it.getContributedDescriptors(DescriptorKindFilter.ALL, nameFilter)
        val members = implicitMembers(it, partialIdentifier)

        locals.asSequence() + members.asSequence()
    }
}

private fun implicitMembers(scope: HierarchicalScope, partialIdentifier: String): Collection<DeclarationDescriptor> {
    if (scope !is LexicalScope) return emptyList()
    val implicit = scope.implicitReceiver ?: return emptyList()

    return completeMembers(implicit.type, partialIdentifier)
}

private fun matchesPartialIdentifier(partialIdentifier: String): (Name) -> Boolean {
    return {
        containsCharactersInOrder(it.identifier, partialIdentifier, false)
    }
}

private fun containsCharactersInOrder(
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