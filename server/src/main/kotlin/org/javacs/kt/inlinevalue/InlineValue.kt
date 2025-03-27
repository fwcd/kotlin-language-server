package org.javacs.kt.inlinevalue

import org.eclipse.lsp4j.InlineValue
import org.eclipse.lsp4j.InlineValueText
import org.eclipse.lsp4j.Range
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.position
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtTokens.INTEGER_LITERAL
import org.jetbrains.kotlin.lexer.KtTokens.FLOAT_LITERAL
import org.jetbrains.kotlin.lexer.KtTokens.CHARACTER_LITERAL
import org.jetbrains.kotlin.lexer.KtTokens.TRUE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.FALSE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.NULL_KEYWORD
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtConstantExpression



fun findInlineValues(file: CompiledFile, range: Range): List<InlineValue> {
    val inlineValues = mutableListOf<InlineValue>()
    val content = file.content
    val bindingContext = file.compile

    // Find all variable declarations in the range
    file.parse.accept(object : KtVisitorVoid() {
        override fun visitProperty(property: KtProperty) {
            val initializer = property.initializer
            if (initializer != null && isInRange(property, range, content)) {
                val value = evaluateExpression(initializer, bindingContext)
                if (value != null) {
                    inlineValues.add(InlineValue(
                        InlineValueText(
                            Range(position(content, property.startOffset), position(content, property.endOffset)),
                            value
                        )
                    ))
                }
            }
        }
})

return inlineValues
}

private fun isInRange(element: KtElement, range: Range, content: String): Boolean {
    val elementRange = Range(position(content, element.startOffset), position(content, element.endOffset))
    return elementRange.start.line >= range.start.line &&
        elementRange.end.line <= range.end.line &&
        !(elementRange.end.line == range.start.line && elementRange.end.character < range.start.character) &&
        !(elementRange.start.line == range.end.line && elementRange.start.character > range.end.character)
}

private fun evaluateExpression(expression: KtExpression, bindingContext: BindingContext): String? {
    return when (expression) {
        is KtStringTemplateExpression -> {
            val resolvedCall = expression.getResolvedCall(bindingContext)
            resolvedCall?.resultingDescriptor?.valueParameters?.firstOrNull()?.let { param ->
                bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, param.findPsi()]?.toString()
            }
        }
        is KtConstantExpression -> {
            when (expression.node.elementType) {
                INTEGER_LITERAL -> expression.text
                FLOAT_LITERAL -> expression.text
                CHARACTER_LITERAL -> expression.text
                TRUE_KEYWORD -> "true"
                FALSE_KEYWORD -> "false"
                NULL_KEYWORD -> "null"
                else -> null
            }
        }
        else -> null
    }
}
