package org.javacs.kt

import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Test
import org.javacs.kt.semantictokens.encodeTokens
import org.javacs.kt.semantictokens.SemanticToken
import org.javacs.kt.semantictokens.SemanticTokenType
import org.javacs.kt.semantictokens.SemanticTokenModifier

class SemanticTokensTest : SingleFileTestFixture("semantictokens", "SemanticTokens.kt") {
    @Test fun `tokenize entire file`() {
        val response = languageServer.textDocumentService.semanticTokensFull(semanticTokensParams(file)).get()!!
        val actual = response.data
        val expected = encodeTokens(sequenceOf(
            SemanticToken(range(1, 5, 1, 13), SemanticTokenType.PROPERTY, setOf(SemanticTokenModifier.DECLARATION)), // variable

            SemanticToken(range(2, 5, 2, 13), SemanticTokenType.PROPERTY, setOf(SemanticTokenModifier.DECLARATION, SemanticTokenModifier.READONLY)), // constant
            SemanticToken(range(2, 15, 2, 21), SemanticTokenType.CLASS), // String
            SemanticToken(range(2, 24, 2, 40), SemanticTokenType.STRING), // "test $variable"
            SemanticToken(range(2, 30, 2, 39), SemanticTokenType.INTERPOLATION_ENTRY), // $variable
            SemanticToken(range(2, 31, 2, 39), SemanticTokenType.PROPERTY), // variable

            SemanticToken(range(4, 12, 4, 16), SemanticTokenType.CLASS, setOf(SemanticTokenModifier.DECLARATION)), // Type
            SemanticToken(range(4, 21, 4, 29), SemanticTokenType.PARAMETER, setOf(SemanticTokenModifier.DECLARATION, SemanticTokenModifier.READONLY)), // property
            SemanticToken(range(4, 31, 4, 34), SemanticTokenType.CLASS), // Int

            SemanticToken(range(6, 5, 6, 6), SemanticTokenType.FUNCTION, setOf(SemanticTokenModifier.DECLARATION)), // f
            SemanticToken(range(6, 7, 6, 8), SemanticTokenType.PARAMETER, setOf(SemanticTokenModifier.DECLARATION, SemanticTokenModifier.READONLY)), // x
            SemanticToken(range(6, 10, 6, 13), SemanticTokenType.CLASS), // Int?
            SemanticToken(range(6, 24, 6, 27), SemanticTokenType.CLASS), // Int
            SemanticToken(range(6, 30, 6, 31), SemanticTokenType.FUNCTION), // f
            SemanticToken(range(6, 32, 6, 33), SemanticTokenType.VARIABLE, setOf(SemanticTokenModifier.READONLY)), // x
        ))

        assertThat(actual, contains(*expected.toTypedArray()))
    }
}
