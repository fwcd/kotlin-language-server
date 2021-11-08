package org.javacs.kt

import org.eclipse.lsp4j.TextDocumentIdentifier
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test

class JavadocTest : LanguageServerTestFixture("javadoc") {
    val file = "FunctionDocumentation.kt"

    @Before
    fun `open DocumentSymbols`() {
        open(file)
    }

    @Test
    fun `find function documentation`() {
        val hover = languageServer.textDocumentService.hover(hoverParams(file, 5, 23)).get()!!
        val contents = hover.contents.right

        assertThat(contents.value, containsString("This method can be called using the index operator"))

    }
}
