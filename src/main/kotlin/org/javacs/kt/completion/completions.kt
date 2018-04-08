package org.javacs.kt.completion

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion
import org.jetbrains.kotlin.resolve.scopes.HierarchicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType

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