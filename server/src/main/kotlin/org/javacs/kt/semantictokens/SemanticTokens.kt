package org.javacs.kt.semantictokens

import org.eclipse.lsp4j.SemanticTokenTypes
import org.eclipse.lsp4j.SemanticTokenModifiers
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.Range
import org.jetbrains.kotlin.psi.KtElement

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

private data class SemanticToken(val range: Range, val type: SemanticTokenType, val modifiers: Set<SemanticTokenModifier>)

private fun semanticTokens(element: KtElement): List<Int> {
    return listOf() // TODO
}

