package org.javacs.kt

import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test

class ReferencesTest: LanguageServerTestFixture("references") {
    val file = "ReferenceTo.kt"

    @Before fun `open ReferenceTo`() {
        open(file)
    }

    @Test fun `find referencs to foo`() {
        val request = ReferenceParams(ReferenceContext(true))
        request.textDocument = TextDocumentIdentifier(uri(file).toString())
        request.position = position(2, 11)
        val references = languageServer.textDocumentService.references(request).get()

        assertThat("Finds references within a file", references, hasItem(hasProperty("uri", containsString("ReferenceTo.kt"))))
        assertThat("Finds references across files", references, hasItem(hasProperty("uri", containsString("ReferenceFrom.kt"))))
    }

    @Test fun `find references to +`() {
        val request = ReferenceParams(ReferenceContext(true))
        request.textDocument = TextDocumentIdentifier(uri(file).toString())
        request.position = position(6, 20)
        val references = languageServer.textDocumentService.references(request).get()

        assertThat("Finds references within a file", references, hasItem(hasProperty("uri", containsString("ReferenceTo.kt"))))
        assertThat("Finds references across files", references, hasItem(hasProperty("uri", containsString("ReferenceFrom.kt"))))
    }
}