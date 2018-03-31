package org.javacs.kt

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.javacs.kt.compiler.PARSER
import org.javacs.kt.compiler.analyzeExpression
import org.javacs.kt.compiler.analyzeFiles
import org.javacs.kt.completion.completeIdentifiers
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.KotlinType
import java.net.URI

class LiveFile(private val fileName: URI, private var text: String) {
    private var file = PARSER.createFile(text)
    private var analyze = analyzeFiles(file)
    /** For testing */
    var reAnalyzed = false

    fun hoverAt(newText: String, cursor: Int): Pair<TextRange, DeclarationDescriptor>? {
        val strategy = recover(newText, cursor) ?: return null
        val start = strategy.oldRange.startOffset
        val leaf = strategy.newExpr.findElementAt(cursor - start) ?: return null
        val elements = leaf.parentsWithSelf.filterIsInstance<PsiElement>()
        val stopAtDeclaration = elements.takeWhile { it !is KtDeclaration }
        val expressionsOnly = stopAtDeclaration.filterIsInstance<KtExpression>()
        val hasHover = expressionsOnly.filter { hoverDecl(it, strategy.newContext) != null }
        val expr = hasHover.firstOrNull() ?: return null
        val range = expr.textRange.shiftRight(strategy.oldRange.startOffset)
        val hover = hoverDecl(expr, strategy.newContext) ?: return null

        return Pair(range, hover)
    }

    private fun hoverDecl(expr: KtExpression, context: BindingContext): DeclarationDescriptor? {
        return when (expr) {
            is KtReferenceExpression -> context.get(BindingContext.REFERENCE_TARGET, expr) ?: return null
            else -> null
        }
    }

    fun completionsAt(newText: String, cursor: Int): Sequence<DeclarationDescriptor> {
        val strategy = recover(newText, cursor) ?: return emptySequence()
        val leaf = strategy.newExpr.findElementAt(cursor - strategy.oldRange.startOffset - 1) ?: return emptySequence()
        val elements = leaf.parentsWithSelf.filterIsInstance<KtElement>()
        val exprs = elements.takeWhile { it is KtExpression }.filterIsInstance<KtExpression>()
        val dot = exprs.filterIsInstance<KtDotQualifiedExpression>().firstOrNull()
        if (dot != null) return completeMembers(dot, strategy).asSequence()
        val ref = exprs.filterIsInstance<KtNameReferenceExpression>().firstOrNull()
        if (ref != null) return completeId(ref, strategy)

        return emptySequence()
    }

    private fun completeId(
            ref: KtNameReferenceExpression,
            strategy: RecoveryStrategy): Sequence<DeclarationDescriptor> {
        val partial = partialId(ref)
        val scope = findScope(ref, strategy.newContext) ?: return emptySequence()

        return completeIdentifiers(scope, partial)
    }

    private fun completeMembers(
            dot: KtDotQualifiedExpression,
            strategy: RecoveryStrategy): Collection<DeclarationDescriptor> {
        val type = strategy.newContext.getType(dot.receiverExpression)
                   ?: robustType(dot.receiverExpression, strategy.newContext)
                   ?: return emptyList()
        val partial = partialId(dot.selectorExpression)

        return org.javacs.kt.completion.completeMembers(type, partial)
    }

    private fun partialId(exprAtCursor: KtExpression?): String {
        val select = exprAtCursor?.text ?: ""
        val word = Regex("[^()]+")

        return word.find(select)?.value ?: ""
    }

    /**
     * If we're having trouble figuring out the type of an expression,
     * try re-parsing and re-analyzing just the difficult expression
     */
    fun robustType(expr: KtExpression, context: BindingContext): KotlinType? {
        val scope = findScope(expr, context) ?: return null
        val parse = PARSER.createExpression(expr.text)
        val analyze = analyzeExpression(parse, scope)

        return analyze.getType(parse)
    }

    private fun findScope(expr: KtExpression, context: BindingContext): LexicalScope? {
        return expr.parentsWithSelf.filterIsInstance<KtElement>().mapNotNull {
            context.get(BindingContext.LEXICAL_SCOPE, it)
        }.firstOrNull()
    }

    /**
     * Try to re-analyze a section of the file, but fall back on full re-compilation if necessary
     */
    private fun recover(newText: String, cursor: Int): RecoveryStrategy? {
        reAnalyzed = false

        // If there are no changes, we can use the existing analyze
        val changed = changedRegion(newText) ?: return NoChanges()
        // Look for a recoverable expression around the cursor
        val strategy = recoverAt(newText, cursor) ?: return fullRecompile(newText)
        // If the expression that we're going to re-compile doesn't include all the changes, give up
        if (!strategy.willRepair.contains(changed)) return fullRecompile(newText)

        return strategy
    }

    private fun fullRecompile(newText: String): RecoveryStrategy? {
        reAnalyze(newText)
        return NoChanges()
    }

    /**
     * Re-analyze the document if it has changed too much to be analyzed incrementally
     */
    private fun reAnalyze(newText: String) {
        LOG.info("Re-analyzing $fileName")

        text = newText
        file = PARSER.createFile(newText)
        analyze = analyzeFiles(file)
        reAnalyzed = true
    }

    /**
     * Region that has been changed, in both old-document and new-document coordinates
     */
    private fun changedRegion(newText: String): TextRange? {
        if (text == newText) return null

        val prefix = text.commonPrefixWith(newText).length
        val suffix = text.commonSuffixWith(newText).length

        return TextRange(prefix, text.length - suffix)
    }

    private fun recoverAt(newText: String, cursor: Int): RecoveryStrategy? {
        val leaf = file.findElementAt(cursor) ?: return null
        val elements = leaf.parentsWithSelf.filterIsInstance<KtElement>()
        val hasScope = elements.mapNotNull { recoveryStrategy(newText, it)}
        return hasScope.firstOrNull()
    }

    fun recoveryStrategy(newText: String, element: KtElement): RecoveryStrategy? {
        return when (element) {
            is KtNamedFunction -> {
                val start = element.textRange.startOffset
                val end = element.textRange.endOffset + newText.length - text.length
                val exprText = newText.substring(start, end)
                val expr = PARSER.createFunction(exprText)
                ReparseFunction(element, expr)
            }
            else -> null
        }
    }

    interface RecoveryStrategy {
        val oldRange: TextRange
        val willRepair: TextRange
        val oldExpr: KtElement
        val newExpr: KtElement
        val newContext: BindingContext
    }

    inner class ReparseFunction(override val oldExpr: KtNamedFunction, override val newExpr: KtNamedFunction): RecoveryStrategy {
        private val oldScope = analyze.bindingContext.get(BindingContext.LEXICAL_SCOPE, oldExpr.bodyExpression)!!
        override val oldRange = oldExpr.textRange
        override val willRepair = oldExpr.bodyExpression!!.textRange
        override val newContext = analyzeExpression(newExpr, oldScope)
    }

    inner class NoChanges: RecoveryStrategy {
        override val oldRange = file.textRange
        override val willRepair = file.textRange
        override val oldExpr = file
        override val newExpr = file
        override val newContext = analyze.bindingContext
    }
}