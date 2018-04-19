package org.javacs.kt

import org.eclipse.lsp4j.Position
import org.javacs.kt.position.position
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType

/**
 * @param content Current contents of the entire file
 * @param parsed An element that surrounds the cursor
 * @param compiled Result of type-checking `parsed`
 * @param cursor The user's cursor
 * @param textOffset Offset between the coordinate system of `parsed` and the coordinates of `cursor`
 */
class CompiledCode(
        val content: String,
        val parsed: KtElement,
        val compiled: BindingContext,
        val cursor: Int,
        val textOffset: Int,
        private val compiler: Compiler,
        private val sourcePath: Collection<KtFile>) {

    /**
     * Convert an offset from relative-to-cursor to absolute-within-parsed, taking into consideration the weirdness of
     */
    fun offset(relativeToCursor: Int): Int = cursor - textOffset + relativeToCursor

    /**
     * If we're having trouble figuring out the type of an expression,
     * try re-parsing and re-analyzing just the difficult expression
     */
    fun robustType(expr: KtExpression): KotlinType? {
        val scope = findScope(expr) ?: return null
        val parse = compiler.createExpression(expr.text)
        val analyze = compiler.compileExpression(parse, scope, sourcePath)

        return analyze.getType(parse)
    }

    /**
     * Find the nearest lexical around an expression
     */
    fun findScope(expr: KtExpression) =
            expr.parentsWithSelf
                    .filterIsInstance<KtElement>()
                    .mapNotNull { compiled.get(BindingContext.LEXICAL_SCOPE, it) }
                    .firstOrNull()
}