package org.javacs.kt

import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.Position
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class DocumentHighlightTest : SingleFileTestFixture("highlight", "DocumentHighlight.kt") {

    @Test
    fun `should highlight input to function`() {
        val fileUri = workspaceRoot.resolve(file).toUri().toString()
        val input = DocumentHighlightParams(TextDocumentIdentifier(fileUri), Position(4, 20))
        val result = languageServer.textDocumentService.documentHighlight(input).get()

        assertThat(result, hasSize(2))
        val firstHighlight = result[0]
        assertThat(firstHighlight.range, equalTo(range(3, 14, 3, 19)))
        assertThat(firstHighlight.kind, equalTo(DocumentHighlightKind.Text))

        val secondHighlight = result[1]
        assertThat(secondHighlight.range, equalTo(range(5, 20, 5, 25)))
        assertThat(secondHighlight.kind, equalTo(DocumentHighlightKind.Text))
    }

    @Test
    fun `should highlight global variable`() {
        val fileUri = workspaceRoot.resolve(file).toUri().toString()
        val input = DocumentHighlightParams(TextDocumentIdentifier(fileUri), Position(3, 23))
        val result = languageServer.textDocumentService.documentHighlight(input).get()

        assertThat(result, hasSize(3))
        val firstHighlight = result[0]
        assertThat(firstHighlight.range, equalTo(range(1, 5, 1, 14)))
        assertThat(firstHighlight.kind, equalTo(DocumentHighlightKind.Text))

        val secondHighlight = result[1]
        assertThat(secondHighlight.range, equalTo(range(4, 23, 4, 32)))
        assertThat(secondHighlight.kind, equalTo(DocumentHighlightKind.Text))

        val thirdHighlight = result[2]
        assertThat(thirdHighlight.range, equalTo(range(8, 13, 8, 22)))
        assertThat(thirdHighlight.kind, equalTo(DocumentHighlightKind.Text))
    }

    @Test
    fun `should highlight global variable when marked from declaration site`() {
        val fileUri = workspaceRoot.resolve(file).toUri().toString()
        val input = DocumentHighlightParams(TextDocumentIdentifier(fileUri), Position(0, 6))
        val result = languageServer.textDocumentService.documentHighlight(input).get()

        assertThat(result, hasSize(3))
        val firstHighlight = result[0]
        assertThat(firstHighlight.range, equalTo(range(1, 5, 1, 14)))
        assertThat(firstHighlight.kind, equalTo(DocumentHighlightKind.Text))

        val secondHighlight = result[1]
        assertThat(secondHighlight.range, equalTo(range(4, 23, 4, 32)))
        assertThat(secondHighlight.kind, equalTo(DocumentHighlightKind.Text))

        val thirdHighlight = result[2]
        assertThat(thirdHighlight.range, equalTo(range(8, 13, 8, 22)))
        assertThat(thirdHighlight.kind, equalTo(DocumentHighlightKind.Text))
    }

    @Test
    fun `should highlight symbols in current file where declaration is in another file`() {
        val fileUri = workspaceRoot.resolve(file).toUri().toString()
        val input = DocumentHighlightParams(TextDocumentIdentifier(fileUri), Position(4, 48))
        val result = languageServer.textDocumentService.documentHighlight(input).get()

        assertThat(result, hasSize(2))
        val firstHighlight = result[0]
        assertThat(firstHighlight.range, equalTo(range(5, 49, 5, 67)))
        assertThat(firstHighlight.kind, equalTo(DocumentHighlightKind.Text))

        val secondHighlight = result[1]
        assertThat(secondHighlight.range, equalTo(range(9, 13, 9, 31)))
        assertThat(secondHighlight.kind, equalTo(DocumentHighlightKind.Text))
    }

    @Test
    fun `should highlight shadowed variable correctly, just show the shadowed variable`() {
        val fileUri = workspaceRoot.resolve(file).toUri().toString()
        val input = DocumentHighlightParams(TextDocumentIdentifier(fileUri), Position(13, 14))
        val result = languageServer.textDocumentService.documentHighlight(input).get()

        assertThat(result, hasSize(2))
        val firstHighlight = result[0]
        assertThat(firstHighlight.range, equalTo(range(13, 15, 13, 24)))
        assertThat(firstHighlight.kind, equalTo(DocumentHighlightKind.Text))

        val secondHighlight = result[1]
        assertThat(secondHighlight.range, equalTo(range(14, 13, 14, 22)))
        assertThat(secondHighlight.kind, equalTo(DocumentHighlightKind.Text))
    }

    @Test
    fun `should highlight function reference correctly`() {
        val fileUri = workspaceRoot.resolve(file).toUri().toString()
        val input = DocumentHighlightParams(TextDocumentIdentifier(fileUri), Position(2, 6))
        val result = languageServer.textDocumentService.documentHighlight(input).get()

        assertThat(result, hasSize(2))
        val firstHighlight = result[0]
        assertThat(firstHighlight.range, equalTo(range(3, 5, 3, 13)))
        assertThat(firstHighlight.kind, equalTo(DocumentHighlightKind.Text))

        val secondHighlight = result[1]
        assertThat(secondHighlight.range, equalTo(range(15, 5, 15, 13)))
        assertThat(secondHighlight.kind, equalTo(DocumentHighlightKind.Text))
    }
}
