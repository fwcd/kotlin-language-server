package org.javacs.kt.semantictokens

import org.eclipse.lsp4j.SemanticTokenTypes
import org.eclipse.lsp4j.SemanticTokenModifiers
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.Range
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.range
import org.javacs.kt.util.preOrderTraversal
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import com.intellij.psi.PsiElement

private enum class SemanticTokenType(val typeName: String) {
    VARIABLE(SemanticTokenTypes.Variable),
    FUNCTION(SemanticTokenTypes.Function),
    PROPERTY(SemanticTokenTypes.Property),
    ENUM_MEMBER(SemanticTokenTypes.EnumMember),
    TYPE(SemanticTokenTypes.Type)
}

private enum class SemanticTokenModifier(val modifierName: String) {
    DECLARATION(SemanticTokenModifiers.Declaration),
    DEFINITION(SemanticTokenModifiers.Definition)
}

val semanticTokensLegend = SemanticTokensLegend(
    SemanticTokenType.values().map { it.typeName },
    SemanticTokenModifier.values().map { it.modifierName }
)

private data class SemanticToken(val range: Range, val type: SemanticTokenType, val modifiers: Set<SemanticTokenModifier> = setOf())

fun semanticTokens(file: CompiledFile): List<Int> =
    encodeTokens(elementTokens(file.parse, file.compile))

private fun encodeTokens(tokens: Sequence<SemanticToken>): List<Int> {
    val encoded = mutableListOf<Int>()
    var last: SemanticToken? = null

    for (token in tokens) {
        // Tokens must be on a single line
        if (token.range.start.line == token.range.end.line) {
            val length = token.range.end.character - token.range.start.character
            val deltaLine = token.range.start.line - (last?.range?.start?.line ?: 0)
            val deltaStart = token.range.start.character - (last?.takeIf { deltaLine == 0 }?.range?.start?.character ?: 0)

            encoded.add(deltaLine)
            encoded.add(deltaStart)
            encoded.add(length)
            encoded.add(encodeType(token.type))
            encoded.add(encodeModifiers(token.modifiers))

            last = token
        }
    }

    return encoded
}

private fun encodeType(type: SemanticTokenType): Int = type.ordinal

private fun encodeModifiers(modifiers: Set<SemanticTokenModifier>): Int = modifiers
    .map { 1 shl it.ordinal }
    .fold(0, Int::or)

private fun elementTokens(element: PsiElement, bindingContext: BindingContext): Sequence<SemanticToken> = element
    .preOrderTraversal()
    .mapNotNull { elementToken(it, bindingContext) }

private fun elementToken(element: PsiElement, bindingContext: BindingContext): SemanticToken? {
    val file = element.containingFile
    val elementRange = range(file.text, element.textRange)
    return when (element) {
        is KtNameReferenceExpression -> {
            val target = bindingContext[BindingContext.REFERENCE_TARGET, element]
            val tokenType = when (target) {
                is PropertyDescriptor -> SemanticTokenType.PROPERTY
                is VariableDescriptor -> SemanticTokenType.VARIABLE
                is FunctionDescriptor -> SemanticTokenType.FUNCTION
                is ClassifierDescriptor -> SemanticTokenType.TYPE
                else -> return null
            }
            SemanticToken(elementRange, tokenType)
        }
        else -> null
    }
}
