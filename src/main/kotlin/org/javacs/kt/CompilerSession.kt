package org.javacs.kt

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.javacs.kt.compiler.PARSER
import org.javacs.kt.compiler.analyzeExpression
import org.javacs.kt.completion.completeIdentifiers
import org.javacs.kt.completion.completeMembers
import org.javacs.kt.completion.completeTypes
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.KotlinType

/**
 * @param surrounding An element that surrounds the cursor
 * @param context Result of type-checking `surrounding`
 * @param cursor The user's cursor
 * @param textOffset Offset between the coordinate system of `surrounding` and the coordinates of `cursor`
 */
class CompilerSession(private val surrounding: KtElement, private val context: BindingContext, private val cursor: Int, private val textOffset: Int) {

    fun hover(): Pair<TextRange, DeclarationDescriptor>? {
        val psi = surrounding.findElementAt(cursor - textOffset) ?: return null
        val expr = psi.parentsWithSelf
                .takeWhile { it !is KtDeclaration }
                .filterIsInstance<KtExpression>()
                .filter { doHover(it) != null }
                .firstOrNull() ?: return null
        val range = expr.textRange.shiftRight(textOffset)
        val hover = doHover(expr)!!

        return Pair(range, hover)
    }

    private fun doHover(expr: KtExpression): DeclarationDescriptor? {
        return when (expr) {
            is KtReferenceExpression -> context.get(BindingContext.REFERENCE_TARGET, expr) ?: return null
            else -> null
        }
    }

    fun completions(): Sequence<DeclarationDescriptor> {
        val psi = surrounding.findElementAt(cursor - textOffset - 1) ?: return emptySequence()
        val expr = find<KtExpression>(psi) ?: return emptySequence()
        val typeParent = find<KtTypeElement>(expr)
        if (typeParent != null) {
            val scope = findScope(expr, context) ?: return emptySequence()
            val partial = matchIdentifier(expr)

            return completeTypes(scope, partial)
        }
        val dotParent = find<KtDotQualifiedExpression>(expr)
        if (dotParent != null) {
            val type = context.getType(dotParent.receiverExpression)
                       ?: robustType(dotParent.receiverExpression, context)
                       ?: return emptySequence()
            val partial = matchIdentifier(dotParent.selectorExpression)

            return completeMembers(type, partial)
        }
        val idParent = find<KtNameReferenceExpression>(expr)
        if (idParent != null) {
            val scope = findScope(idParent, context) ?: return emptySequence()
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
}

private inline fun<reified Find> find(cursor: PsiElement) =
        cursor.parentsWithSelf.filterIsInstance<Find>().firstOrNull()