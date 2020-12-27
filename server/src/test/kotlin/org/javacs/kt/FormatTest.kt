package org.javacs.kt

import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.FormattingOptions
import org.junit.Test
import org.junit.Ignore
import org.junit.Assert.assertThat
import org.hamcrest.Matchers.equalTo

class FormatTest : SingleFileTestFixture("formatting", "NonFormatted.kt") {
    @Test fun `format kotlin code`() {
        val edits = languageServer.textDocumentService.formatting(DocumentFormattingParams(
            TextDocumentIdentifier(uri(file).toString()),
            FormattingOptions(
                4, // tabSize
                true // insertSpaces
            )
        )).get()!!
        assertThat(edits.size, equalTo(1))
        assertThat(edits[0].newText.replace("\r\n", "\n"), equalTo("""class Door(val width: Int = 3, val height: Int = 4)

class House {
    val door = Door()

    val window = "Window"
}
""".replace("\r\n", "\n")))
    }
}

class FormatToLineTest : SingleFileTestFixture("formatting", "Spaces.kt") {
    @Test fun `format to single line`() {
        val formatted = languageServer.textDocumentService.formatting(DocumentFormattingParams(
            TextDocumentIdentifier(uri(file).toString()),
            FormattingOptions()
        )).get()!![0].newText
        assertThat(formatted.replace("\r\n", "\n"), equalTo("class Test(val a: String, val b: String)\n"))
    }
}
