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
        assertThat(edits[0].newText.replace("\r\n", "\n"), equalTo("""class Door(
    val width: Int = 3,
    val height: Int = 4
)

class House {
    val door = Door()

    val window = "Window"
}
""".replace("\r\n", "\n")))
    }
}

class Format2SpacesTest : SingleFileTestFixture("formatting", "Spaces.kt") {
    @Test fun `format with 2 spaces`() {
        val formatted = languageServer.textDocumentService.formatting(DocumentFormattingParams(
            TextDocumentIdentifier(uri(file).toString()),
            FormattingOptions(
                2, // tabSize,
                true // insertSpaces
            )
        )).get()!![0].newText
        assertThat(formatted.replace("\r\n", "\n"), equalTo("class Test(\n  val a: String,\n  val b: String\n)\n"))
    }
}

class FormatTabsTest : SingleFileTestFixture("formatting", "Spaces.kt") {
    // TODO: Tabs are not yet supported by ktlint,
    //       see https://github.com/pinterest/ktlint/issues/128
    @Ignore @Test fun `format with tabs`() {
        val formatted = languageServer.textDocumentService.formatting(DocumentFormattingParams(
            TextDocumentIdentifier(uri(file).toString()),
            FormattingOptions(
                2, // tabSize,
                false // insertSpaces
            )
        )).get()!![0].newText
        assertThat(formatted.replace("\r\n", "\n"), equalTo("class Test(\n\tval a: String,\n\tval b: String\n)\n"))
    }
}
