package org.javacs.kt

import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Test
import org.javacs.kt.semantictokens.encodeTokens
import org.javacs.kt.semantictokens.SemanticToken
import org.javacs.kt.semantictokens.SemanticTokenType
import org.javacs.kt.semantictokens.SemanticTokenModifier

class SemanticTokensTest : SingleFileTestFixture("semantictokens", "SemanticTokens.kt") {
    @Test fun `tokenize file`() {
        val varLine = 1
        val constLine = 2
        val classLine = 4
        val funLine = 6
        val enumLine = 8

        val expectedVar = sequenceOf(
            SemanticToken(range(varLine, 5, varLine, 13), SemanticTokenType.PROPERTY, setOf(SemanticTokenModifier.DECLARATION)), // variable
        )
        val expectedConst = sequenceOf(
            SemanticToken(range(constLine, 5, constLine, 13), SemanticTokenType.PROPERTY, setOf(SemanticTokenModifier.DECLARATION, SemanticTokenModifier.READONLY)), // constant
            SemanticToken(range(constLine, 15, constLine, 21), SemanticTokenType.CLASS), // String
            SemanticToken(range(constLine, 24, constLine, 40), SemanticTokenType.STRING), // "test $variable"
            SemanticToken(range(constLine, 30, constLine, 39), SemanticTokenType.INTERPOLATION_ENTRY), // $variable
            SemanticToken(range(constLine, 31, constLine, 39), SemanticTokenType.PROPERTY), // variable
        )
        val expectedClass = sequenceOf(
            SemanticToken(range(classLine, 12, classLine, 16), SemanticTokenType.CLASS, setOf(SemanticTokenModifier.DECLARATION)), // Type
            SemanticToken(range(classLine, 21, classLine, 29), SemanticTokenType.PARAMETER, setOf(SemanticTokenModifier.DECLARATION, SemanticTokenModifier.READONLY)), // property
            SemanticToken(range(classLine, 31, classLine, 34), SemanticTokenType.CLASS), // Int
        )
        val expectedFun = sequenceOf(
            SemanticToken(range(funLine, 5, funLine, 6), SemanticTokenType.FUNCTION, setOf(SemanticTokenModifier.DECLARATION)), // f
            SemanticToken(range(funLine, 7, funLine, 8), SemanticTokenType.PARAMETER, setOf(SemanticTokenModifier.DECLARATION, SemanticTokenModifier.READONLY)), // x
            SemanticToken(range(funLine, 10, funLine, 13), SemanticTokenType.CLASS), // Int?
            SemanticToken(range(funLine, 24, funLine, 27), SemanticTokenType.CLASS), // Int
            SemanticToken(range(funLine, 30, funLine, 31), SemanticTokenType.FUNCTION), // f
            SemanticToken(range(funLine, 32, funLine, 33), SemanticTokenType.VARIABLE, setOf(SemanticTokenModifier.READONLY)), // x
        )
        val expectedEnum = sequenceOf(
            SemanticToken(range(enumLine, 12, enumLine, 16), SemanticTokenType.CLASS, setOf(SemanticTokenModifier.DECLARATION)), // Enum
            SemanticToken(range(enumLine, 19, enumLine, 27), SemanticTokenType.ENUM_MEMBER, setOf(SemanticTokenModifier.DECLARATION)) // Variant1
        )

        val partialExpected = encodeTokens(expectedConst + expectedClass)
        val partialResponse = languageServer.textDocumentService.semanticTokensRange(semanticTokensRangeParams(file, range(constLine, 0, classLine + 1, 0))).get()!!
        assertThat(partialResponse.data, contains(*partialExpected.toTypedArray()))

        val fullExpected = encodeTokens(expectedVar + expectedConst + expectedClass + expectedFun + expectedEnum)
        val fullResponse = languageServer.textDocumentService.semanticTokensFull(semanticTokensParams(file)).get()!!
        assertThat(fullResponse.data, contains(*fullExpected.toTypedArray()))
    }
}
