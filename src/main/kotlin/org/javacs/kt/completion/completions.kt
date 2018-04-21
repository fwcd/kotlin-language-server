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
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion
import org.jetbrains.kotlin.resolve.scopes.HierarchicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import java.util.concurrent.TimeUnit

private const val MAX_COMPLETION_ITEMS = 50

fun completions(code: CompiledCode): CompletionList {
    val completions = doCompletions(code)
    val partial = findPartialIdentifier(code)
    val nameFilter = matchesPartialIdentifier(partial)
    val matchesName = completions.filter(nameFilter)
    val visible = matchesName.filter(isVisible(code))
    val list = visible.map(::completionItem).take(MAX_COMPLETION_ITEMS).toList()
    val isIncomplete = list.size == MAX_COMPLETION_ITEMS
    // TODO separate "get all candidates" from "filter by name"
    return CompletionList(isIncomplete, list)
}

private fun completionItem(declaration: DeclarationDescriptor): CompletionItem =
        declaration.accept(RenderCompletionItem(), null)

private fun doCompletions(code: CompiledCode): Sequence<DeclarationDescriptor> {
    val expr = exprBeforeCursor(code) ?: return emptySequence()
    // :?
    val typeParent = expr.findParent<KtTypeElement>()
    if (typeParent != null) {
        val scope = code.findScope(expr) ?: return emptySequence()

        return scopeChainTypes(scope)
    }
    // .?
    val dotParent = expr.findParent<KtDotQualifiedExpression>()
    if (dotParent != null) {
        // thingWithType.?
        val receiver = dotParent.receiverExpression
        val type = robustType(receiver, code)
        if (type != null) {
            val members = type.memberScope.getContributedDescriptors(DescriptorKindFilter.ALL).asSequence()
            val lexicalScope = code.findScope(dotParent) ?: return cantFindLexicalScope(dotParent)
            val extensions = extensionFunctions(lexicalScope).filter { isExtensionFor(type, it) }

            return members + extensions
        }
        // JavaClass.?
        val static = staticScope(receiver, code.compiled)
        if (static != null) {
            return static.getContributedDescriptors(DescriptorKindFilter.ALL).asSequence()
        }

        LOG.info("Can't find member scope for ${dotParent.text}")
        return emptySequence()
    }
    // ?
    val idParent = expr.findParent<KtNameReferenceExpression>()
    if (idParent != null) {
        val scope = code.findScope(idParent) ?: return cantFindLexicalScope(idParent)

        return identifiers(scope)
    }

    LOG.info("$expr ${expr.text} didn't look like a type, a member, or an identifier")
    return emptySequence()
}

private fun exprBeforeCursor(code: CompiledCode): KtExpression? =
    code.parsed.findElementAt(code.offset(-1))?.findParent<KtExpression>()

private fun robustType(expr: KtExpression, code: CompiledCode): KotlinType? =
        code.compiled.getType(expr) ?: code.robustType(expr)

private fun staticScope(expr: KtExpression, context: BindingContext): MemberScope? =
        expr.getReferenceTargets(context).filterIsInstance<ClassDescriptor>().map { it.staticScope }.firstOrNull()

private fun <T> cantFindLexicalScope(expr: KtExpression): Sequence<T> {
    LOG.info("Can't find lexical scope for ${expr.text}")

    return emptySequence()
}

private fun findPartialIdentifier(code: CompiledCode): String {
    val expr = exprBeforeCursor(code) ?: return ""
    val dotParent = expr.findParent<KtDotQualifiedExpression>()
    if (dotParent != null) return findMember(dotParent)
    else return findId(expr)
}

private val FIND_ID = Regex("\\w+")

private fun findId(expr: KtExpression): String {
    val match = FIND_ID.findAll(expr.text).firstOrNull() ?: return ""
    return match.value
}

private val FIND_MEMBER = Regex("\\.(\\w+)")

private fun findMember(dotExpr: KtDotQualifiedExpression): String {
    val match = FIND_MEMBER.find(dotExpr.text) ?: return ""
    val word = match.groups[1] ?: return ""
    return word.value
}

fun memberOverloads(type: KotlinType, identifier: String): Sequence<CallableDescriptor> {
    val nameFilter = equalsIdentifier(identifier)

    return type.memberScope
            .getContributedDescriptors(Companion.CALLABLES).asSequence()
            .filterIsInstance<CallableDescriptor>()
            .filter(nameFilter)
}

private fun scopeChainTypes(scope: LexicalScope): Sequence<DeclarationDescriptor> =
        scope.parentsWithSelf.flatMap(::scopeTypes)

private val TYPES_FILTER = DescriptorKindFilter(DescriptorKindFilter.NON_SINGLETON_CLASSIFIERS_MASK or DescriptorKindFilter.TYPE_ALIASES_MASK)

private fun scopeTypes(scope: HierarchicalScope): Sequence<DeclarationDescriptor> =
        scope.getContributedDescriptors(TYPES_FILTER).asSequence()

fun identifierOverloads(scope: LexicalScope, identifier: String): Sequence<CallableDescriptor> {
    val nameFilter = equalsIdentifier(identifier)

    return identifiers(scope)
            .filterIsInstance<CallableDescriptor>()
            .filter(nameFilter)
}

private fun extensionFunctions(scope: LexicalScope): Sequence<CallableDescriptor> =
    scope.parentsWithSelf.flatMap(::scopeExtensionFunctions)

private fun scopeExtensionFunctions(scope: HierarchicalScope): Sequence<CallableDescriptor> =
    scope.getContributedDescriptors(DescriptorKindFilter.CALLABLES).asSequence()
            .filterIsInstance<CallableDescriptor>()
            .filter { it.isExtension }

private fun identifiers(scope: LexicalScope): Sequence<DeclarationDescriptor> =
    scope.parentsWithSelf
            .flatMap(::scopeIdentifiers)
            .flatMap(::explodeConstructors)

private fun scopeIdentifiers(scope: HierarchicalScope): Sequence<DeclarationDescriptor> {
    val locals = scope.getContributedDescriptors(DescriptorKindFilter.ALL).asSequence()
    val members = implicitMembers(scope)

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

private fun implicitMembers(scope: HierarchicalScope): Sequence<DeclarationDescriptor> {
    if (scope !is LexicalScope) return emptySequence()
    val implicit = scope.implicitReceiver ?: return emptySequence()

    return implicit.type.memberScope.getContributedDescriptors(DescriptorKindFilter.ALL).asSequence()
}

private fun equalsIdentifier(identifier: String): (DeclarationDescriptor) -> Boolean {
    return { name(it) == identifier }
}

private fun matchesPartialIdentifier(partialIdentifier: String): (DeclarationDescriptor) -> Boolean {
    return {
        containsCharactersInOrder(name(it), partialIdentifier, false)
    }
}

private fun name(d: DeclarationDescriptor): String {
    if (d is ConstructorDescriptor)
        return d.constructedClass.name.identifier
    else
        return d.name.identifier
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

    return fromParents.any { it.fqNameSafe == targetParent.fqNameSafe }
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

private fun isExtensionFor(type: KotlinType, extensionFunction: CallableDescriptor): Boolean {
    val receiverType = extensionFunction.extensionReceiverParameter?.type ?: return false
    return TypeUtils.contains(type, receiverType)
}

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