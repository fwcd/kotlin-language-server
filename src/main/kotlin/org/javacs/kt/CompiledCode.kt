package org.javacs.kt

import com.intellij.openapi.util.TextRange
import org.javacs.kt.completion.completeIdentifiers
import org.javacs.kt.completion.completeMembers
import org.javacs.kt.completion.completeTypes
import org.javacs.kt.position.findParent
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.KotlinType

/**
 * @param fileContent Current contents of the entire file
 * @param surrounding An element that surrounds the cursor
 * @param context Result of type-checking `surrounding`
 * @param cursor The user's cursor
 * @param textOffset Offset between the coordinate system of `surrounding` and the coordinates of `cursor`
 */
class CompiledCode(
        val fileContent: String,
        private val surrounding: KtElement,
        private val context: BindingContext,
        private val cursor: Int,
        private val textOffset: Int,
        private val compiler: Compiler,
        private val sourcePath: Collection<KtFile>) {

    fun hover(): Pair<TextRange, DeclarationDescriptor>? {
        val psi = surrounding.findElementAt(cursor - textOffset) ?: return null
        val stopAtDeclaration = psi.parentsWithSelf.takeWhile { it !is KtDeclaration }
        val onlyExpressions = stopAtDeclaration.filterIsInstance<KtExpression>()
        val hasHover = onlyExpressions.filter { doHover(it) != null }
        val expr = hasHover.firstOrNull() ?: return null
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
        val expr = psi.findParent<KtExpression>() ?: return emptySequence()
        val typeParent = expr.findParent<KtTypeElement>()
        if (typeParent != null) {
            val scope = findScope(expr) ?: return emptySequence()
            val partial = matchIdentifier(expr)

            return completeTypes(scope, partial)
        }
        val dotParent = expr.findParent<KtDotQualifiedExpression>()
        if (dotParent != null) {
            val type = getType(dotParent.receiverExpression) ?: return emptySequence()
            val partial = matchIdentifier(dotParent.selectorExpression)

            return completeMembers(type, partial)
        }
        val idParent = expr.findParent<KtNameReferenceExpression>()
        if (idParent != null) {
            val scope = findScope(idParent) ?: return emptySequence()
            val partial = matchIdentifier(expr)

            return completeIdentifiers(scope, partial)
        }

        return emptySequence()
    }

    fun cursor() =
            cursor - textOffset

    fun exprAt(relativeToCursor: Int) =
            surrounding.findElementAt(cursor - textOffset + relativeToCursor)

    fun getType(expr: KtExpression) =
            context.getType(expr) ?: robustType(expr)

    /**
     * If we're having trouble figuring out the type of an expression,
     * try re-parsing and re-analyzing just the difficult expression
     */
    private fun robustType(expr: KtExpression): KotlinType? {
        val scope = findScope(expr) ?: return null
        val parse = compiler.createExpression(expr.text)
        val analyze = compiler.compileExpression(parse, scope, sourcePath)

        return analyze.getType(parse)
    }

    fun findScope(expr: KtExpression): LexicalScope? {
        return expr.parentsWithSelf.filterIsInstance<KtElement>().mapNotNull {
            context.get(BindingContext.LEXICAL_SCOPE, it)
        }.firstOrNull()
    }
}

private fun matchIdentifier(exprAtCursor: KtExpression?): String {
    val select = exprAtCursor?.text ?: ""
    val word = Regex("[^()]+")

    return word.find(select)?.value ?: ""
}