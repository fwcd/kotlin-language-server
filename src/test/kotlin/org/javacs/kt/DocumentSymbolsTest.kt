package org.javacs.kt

import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test

class DocumentSymbolsTest: LanguageServerTestFixture("symbols") {
    val file = "DocumentSymbols.kt"

    @Before fun `open DocumentSymbols`() {
        open(file)
    }

    @Test fun `find document symbols`() {
        val fileId = TextDocumentIdentifier(uri(file).toString())
        val found = languageServer.textDocumentService.documentSymbol(DocumentSymbolParams(fileId)).get()
        val byKind = found.groupBy({ it.left.kind }, { it.left.name })
        val all = found.map { it.left.name }.toList()

        assertThat(byKind[SymbolKind.Class], hasItem("DocumentSymbols"))
        assertThat(byKind[SymbolKind.Constructor], hasItem("DocumentSymbols"))
        assertThat(byKind[SymbolKind.Property], hasItem("aProperty"))
        assertThat(byKind[SymbolKind.Function], hasItem("aFunction"))
        assertThat(all, not(hasItem("aFunctionArg")))
        assertThat(all, not(hasItem("aConstructorArg")))
    }
}