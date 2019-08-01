package org.javacs.kt

import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.FormattingOptions
import org.junit.Test
import org.junit.Assert.assertThat
import org.hamcrest.Matchers.equalTo

class FormatterTest : SingleFileTestFixture("formatting", "NonFormatted.kt") {
    @Test fun `format kotlin code`() {
        val edits = languageServer.textDocumentService.formatting(DocumentFormattingParams(
            TextDocumentIdentifier(uri(file).toString()),
            FormattingOptions(
                4, // tabSize
                false // insertSpaces
            )
        )).get()!!
        assertThat(edits.size, equalTo(1))
        assertThat(edits[0].newText, equalTo("""class Door(
    val width: Int = 3,
    val height: Int = 4
)

class House {
    val door = Door()

    val window = "Window"
}
"""))
    }
}
