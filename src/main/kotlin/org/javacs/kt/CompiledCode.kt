package org.javacs.kt

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
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
        val textOffset: Int,
        private val compiler: Compiler,
        private val sourcePath: Collection<KtFile>) {

    fun cursor(): Int =
            cursor - textOffset

    fun exprAt(relativeToCursor: Int): PsiElement? =
            surrounding.findElementAt(cursor - textOffset + relativeToCursor)

    fun getType(expr: KtExpression): KotlinType? =
            context.getType(expr) ?: robustType(expr)

    fun getDeclaration(expr: PsiElement): DeclarationDescriptor? =
            context.get(BindingContext.DECLARATION_TO_DESCRIPTOR, expr)

    fun referenceTarget(expr: KtReferenceExpression): DeclarationDescriptor? =
            context.get(BindingContext.REFERENCE_TARGET, expr)

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

    fun findScope(expr: KtExpression) =
            expr.parentsWithSelf
                    .filterIsInstance<KtElement>()
                    .mapNotNull { context.get(BindingContext.LEXICAL_SCOPE, it) }
                    .firstOrNull()
}