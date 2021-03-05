package org.javacs.kt.semantictokens

import org.eclipse.lsp4j.SemanticTokenTypes
import org.eclipse.lsp4j.SemanticTokenModifiers
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.Range
import org.javacs.kt.position.range
import org.javacs.kt.util.preOrderTraversal
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import com.intellij.psi.PsiElement

private enum class SemanticTokenType(val typeName: String) {
    VARIABLE(SemanticTokenTypes.Variable),
    PROPERTY(SemanticTokenTypes.Property),
    ENUM_MEMBER(SemanticTokenTypes.EnumMember)
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

fun semanticTokens(element: PsiElement): List<Int> = encodeTokens(elementTokens(element))

private fun encodeTokens(tokens: Sequence<SemanticToken>): List<Int> {
    val encoded = mutableListOf<Int>()
    var last: SemanticToken? = null

    for (token in tokens) {
        // Tokens must be on a single line
        if (token.range.start.line == token.range.end.line) {
            val deltaLine = token.range.start.line - (last?.let { it.range.start.line } ?: 0)
            val deltaStart = token.range.start.character - (last?.let { it.range.start.character } ?: 0)
            val length = token.range.end.character - token.range.start.character

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

private fun elementTokens(element: PsiElement): Sequence<SemanticToken> = element
    .preOrderTraversal()
    .mapNotNull { (it as? KtNamedDeclaration)?.nameIdentifier }
    .mapNotNull { elementToken(it) }

private fun elementToken(element: PsiElement): SemanticToken? {
    val file = element.containingFile
    val elementRange = range(file.text, element.textRange)
    return when (element) {
        is KtProperty -> SemanticToken(elementRange, SemanticTokenType.PROPERTY)
        is KtVariableDeclaration -> SemanticToken(elementRange, SemanticTokenType.VARIABLE)
        else -> null
    }
}
