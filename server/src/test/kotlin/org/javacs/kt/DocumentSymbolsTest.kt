package org.javacs.kt

import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DocumentSymbolsTest : LanguageServerTestFixture("symbols") {
    val file = "DocumentSymbols.kt"

    @Before fun `open DocumentSymbols`() {
        open(file)
    }

    @Test fun `find document symbols`() {
        val fileId = TextDocumentIdentifier(uri(file).toString())
        val found = languageServer.textDocumentService.documentSymbol(DocumentSymbolParams(fileId)).get()
        val expected = listOf(DocumentSymbol("DocumentSymbols", SymbolKind.Class, Range(Position(0, 0), Position(8, 1)), Range(Position(0, 14), Position(0, 29)), null, listOf(
            DocumentSymbol("DocumentSymbols", SymbolKind.Constructor, Range(Position(0, 29), Position(0, 31)), Range(Position(0, 29), Position(0, 31)), null, listOf()),
            DocumentSymbol("aProperty", SymbolKind.Property, Range(Position(1, 4), Position(1, 21)), Range(Position(1, 8), Position(1, 17)), null, listOf()),
            DocumentSymbol("aFunction", SymbolKind.Function, Range(Position(3, 4), Position(4, 5)), Range(Position(3, 8), Position(3, 17)), null, listOf()),
            DocumentSymbol("DocumentSymbols", SymbolKind.Constructor, Range(Position(6, 4), Position(7, 5)), Range(Position(6, 4), Position(7, 5)), null, listOf())
        )))
        val all = found.map { it.right }.toList()

        assertEquals(all.toString(), expected, all)
    }
}
