package org.javacs.kt.completion

import com.google.common.cache.CacheBuilder
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.javacs.kt.CompiledFile
import org.javacs.kt.LOG
import org.javacs.kt.util.findParent
import org.javacs.kt.util.noResult
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter.Companion
import org.jetbrains.kotlin.resolve.scopes.HierarchicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.supertypes
import java.util.concurrent.TimeUnit

private const val MAX_COMPLETION_ITEMS = 50

fun completions(file: CompiledFile, cursor: Int): CompletionList {
    val surroundingElement = completableElement(file, cursor) ?: return CompletionList(true, emptyList())
    val completions = doCompletions(file, cursor, surroundingElement)
    val partial = findPartialIdentifier(file, cursor)
    LOG.debug("Looking for names that match '{}'", partial)
    val nameFilter = matchesPartialIdentifier(partial)
    val matchesName = completions.filter(nameFilter)
    val visible = matchesName.filter(isVisible(file, cursor))
    val list = visible.map { completionItem(it, surroundingElement, file) }.take(MAX_COMPLETION_ITEMS).toList()
    val isIncomplete = (list.size == MAX_COMPLETION_ITEMS)
    return CompletionList(isIncomplete, list)
}

private val callPattern = Regex("(.*)\\((\\$\\d+)?\\)")
private val methodSignature = Regex("""(?:fun|constructor) (?:<(?:[a-zA-Z\?\!\: ]+)(?:, [A-Z])*> )?([a-zA-Z]+\(.*\))""")

private fun completionItem(d: DeclarationDescriptor, surroundingElement: KtElement, file: CompiledFile): CompletionItem {
    val result = d.accept(RenderCompletionItem(), null)

    result.label = methodSignature.find(result.detail)?.groupValues?.get(1) ?: result.label

    if (isGetter(d) || isSetter(d)) {
        val name = extractVarName(d)

        result.detail += " (from ${result.label})"
        result.label = name
        result.insertText = name
        result.filterText = name
    }

    if (surroundingElement is KtCallableReferenceExpression && result.insertText.endsWith("()")) {
        result.insertText = result.insertText.removeSuffix("()")
    }

    val matchCall = callPattern.matchEntire(result.insertText)
    if (file.lineAfter(surroundingElement.endOffset).startsWith("(") && matchCall != null) {
        result.insertText = matchCall.groups[1]!!.value
    }

    return result
}

private fun extractVarName(d: DeclarationDescriptor): String {
    val match = Regex("(get|is|set)([A-Z]\\w+)").matchEntire(d.name.identifier)!!
    val upper = match.groups[2]!!.value
    return upper[0].toLowerCase() + upper.substring(1)
}

private fun isGetter(d: DeclarationDescriptor): Boolean =
        d is CallableDescriptor &&
        !d.name.isSpecial &&
        d.name.identifier.matches(Regex("(get|is)[A-Z]\\w+")) &&
        d.valueParameters.isEmpty()

private fun isSetter(d: DeclarationDescriptor): Boolean =
        d is CallableDescriptor &&
        !d.name.isSpecial &&
        d.name.identifier.matches(Regex("set[A-Z]\\w+")) &&
        d.valueParameters.size == 1

private fun completableElement(file: CompiledFile, cursor: Int): KtElement? {
    val el = file.parseAtPoint(cursor - 1) ?: return null
            // import x.y.?
    return el.findParent<KtImportDirective>() ?:
            // :?
            el.parent as? KtTypeElement ?:
            // .?
            el as? KtQualifiedExpression ?: el.parent as? KtQualifiedExpression ?:
            // something::?
            el as? KtCallableReferenceExpression ?: el.parent as? KtCallableReferenceExpression ?:
            // ?
            el as? KtNameReferenceExpression
}

private fun doCompletions(file: CompiledFile, cursor: Int, surroundingElement: KtElement): Sequence<DeclarationDescriptor> {
    return when (surroundingElement) {
        // import x.y.?
        is KtImportDirective -> {
            LOG.info("Completing import '{}'", surroundingElement.text)
            val module = file.container.get<ModuleDescriptor>()
            val match = Regex("import ((\\w+\\.)*)[\\w*]*").matchEntire(surroundingElement.text) ?: return doesntLookLikeImport(surroundingElement)
            val parentDot = if (match.groupValues[1].isNotBlank()) match.groupValues[1] else "."
            val parent = parentDot.substring(0, parentDot.length - 1)
            LOG.debug("Looking for members of package '{}'", parent)
            val parentPackage = module.getPackage(FqName.fromSegments(parent.split('.')))
            parentPackage.memberScope.getContributedDescriptors().asSequence()
        }
        // :?
        is KtTypeElement -> {
            // : Outer.?
            if (surroundingElement is KtUserType && surroundingElement.qualifier != null) {
                val referenceTarget = file.referenceAtPoint(surroundingElement.qualifier!!.startOffset)?.second
                if (referenceTarget is ClassDescriptor) {
                    LOG.info("Completing members of {}", referenceTarget.fqNameSafe)
                    return referenceTarget.unsubstitutedInnerClassesScope.getContributedDescriptors().asSequence()
                } else {
                    LOG.warn("No type reference in '{}'", surroundingElement.text)
                    return emptySequence()
                }
            } else {
                // : ?
                LOG.info("Completing type identifier '{}'", surroundingElement.text)
                val scope = file.scopeAtPoint(cursor) ?: return emptySequence()
                scopeChainTypes(scope)
            }
        }
        // .?
        is KtQualifiedExpression -> {
            LOG.info("Completing member expression '{}'", surroundingElement.text)
            completeMembers(file, cursor, surroundingElement.receiverExpression)
        }
        is KtCallableReferenceExpression -> {
            // something::?
            if (surroundingElement.receiverExpression != null) {
                LOG.info("Completing method reference '{}'", surroundingElement.text)
                completeMembers(file, cursor, surroundingElement.receiverExpression!!)
            }
            // ::?
            else {
                LOG.info("Completing function reference '{}'", surroundingElement.text)
                val scope = file.scopeAtPoint(surroundingElement.startOffset) ?: return noResult("No scope at ${file.describePosition(cursor)}", emptySequence())
                identifiers(scope)
            }
        }
        // ?
        is KtNameReferenceExpression -> {
            LOG.info("Completing identifier '{}'", surroundingElement.text)
            val scope = file.scopeAtPoint(surroundingElement.startOffset) ?: return noResult("No scope at ${file.describePosition(cursor)}", emptySequence())
            identifiers(scope)
        }
        else -> {
            LOG.info("{} {} didn't look like a type, a member, or an identifier", surroundingElement::class.simpleName, surroundingElement.text)
            emptySequence()
        }
    }
}

private fun completeMembers(file: CompiledFile, cursor: Int, receiverExpr: KtExpression): Sequence<DeclarationDescriptor> {
    // thingWithType.?
    val lexicalScope = file.scopeAtPoint(cursor)
    if (lexicalScope != null) {
        val receiverType = file.typeOfExpression(receiverExpr, lexicalScope)
        if (receiverType != null) {
            LOG.debug("Completing members of instance '{}'", receiverType)
            val members = receiverType.memberScope.getContributedDescriptors().asSequence()
            val extensions = extensionFunctions(lexicalScope).filter { isExtensionFor(receiverType, it) }
            return members + extensions
        }
    }
    // JavaClass.?
    val referenceTarget = file.referenceAtPoint(receiverExpr.endOffset - 1)?.second
    if (referenceTarget is ClassDescriptor) {
        LOG.debug("Completing static members of '{}'", referenceTarget.fqNameSafe)
        val statics = referenceTarget.staticScope.getContributedDescriptors().asSequence()
        val classes = referenceTarget.unsubstitutedInnerClassesScope.getContributedDescriptors().asSequence()
        return statics + classes
    }

    LOG.debug("Can't find member scope for {}", receiverExpr.text)
    return emptySequence()
}

private fun findPartialIdentifier(file: CompiledFile, cursor: Int): String {
    val line = file.lineBefore(cursor)
    if (line.matches(Regex(".*\\."))) return ""
    else if (line.matches(Regex(".*\\.\\w+"))) return line.substringAfterLast(".")
    else return Regex("\\w+").findAll(line).lastOrNull()?.value ?: ""
}

fun memberOverloads(type: KotlinType, identifier: String): Sequence<CallableDescriptor> {
    val nameFilter = equalsIdentifier(identifier)

    return type.memberScope
            .getContributedDescriptors(Companion.CALLABLES).asSequence()
            .filterIsInstance<CallableDescriptor>()
            .filter(nameFilter)
}

private fun completeTypeMembers(type: KotlinType): Sequence<DeclarationDescriptor> =
    type.memberScope.getDescriptorsFiltered(TYPES_FILTER).asSequence()

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
    val locals = scope.getContributedDescriptors().asSequence()
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

    return implicit.type.memberScope.getContributedDescriptors().asSequence()
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

private fun isVisible(file: CompiledFile, cursor: Int): (DeclarationDescriptor) -> Boolean {
    val el = file.elementAtPoint(cursor) ?: return { true }
    val from = el.parentsWithSelf
                       .mapNotNull { file.compile[BindingContext.DECLARATION_TO_DESCRIPTOR, it] }
                       .firstOrNull() ?: return { true }
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
    val receiverClass = TypeUtils.getClassDescriptor(receiverType) ?: return false
    fun equalsReceiver(type: KotlinType) = TypeUtils.getClassDescriptor(type)?.fqNameSafe == receiverClass.fqNameSafe
    if (equalsReceiver(type)) return true
    else return type.supertypes().any(::equalsReceiver)
}

private val loggedHidden = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).build<Pair<Name, Name>, Unit>()

private fun logHidden(target: DeclarationDescriptor, from: DeclarationDescriptor) {
    val key = Pair(from.name, target.name)

    loggedHidden.get(key, { doLogHidden(target, from )})
}

private fun doLogHidden(target: DeclarationDescriptor, from: DeclarationDescriptor) {
    LOG.debug("Hiding {} because it's not visible from {}", describeDeclaration(target), describeDeclaration(from))
}

private fun describeDeclaration(declaration: DeclarationDescriptor): String {
    val file = declaration.findPsi()?.containingFile?.toPath()?.fileName?.toString() ?: "<unknown-file>"
    val container = declaration.containingDeclaration?.name?.toString() ?: "<top-level>"

    return "($file $container.${declaration.name})"
}

private fun doesntLookLikeImport(importDirective: KtImportDirective): Sequence<DeclarationDescriptor> {
    LOG.debug("{} doesn't look like import a.b...", importDirective.text)

    return emptySequence()
}

private fun empty(message: String): CompletionList {
    LOG.debug(message)

    return CompletionList(true, emptyList())
}
