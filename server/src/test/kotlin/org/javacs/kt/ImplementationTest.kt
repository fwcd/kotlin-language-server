package org.javacs.kt

import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasSize
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class ImplementationTest : SingleFileTestFixture("implementation", "Interface.kt") {

    private fun implementationParams(relativePath: String, line: Int, column: Int): ImplementationParams {
        val file = workspaceRoot.resolve(relativePath)
        val fileId = TextDocumentIdentifier(file.toUri().toString())
        return ImplementationParams(fileId, position(line, column))
    }

    @Test
    fun `find implementations of interface`() {
        // Cursor on "Animal" interface name (line 1, col 11)
        val implementations = languageServer.textDocumentService.implementation(implementationParams(file, 1, 11)).get().left
        val uris = implementations.map { it.uri }

        assertThat(implementations, hasSize(2))
        assertThat(uris, hasItem(containsString("Interface.kt")))
    }

    @Test
    fun `find implementations of abstract class`() {
        // Cursor on "Pet" abstract class name (line 6, col 16)
        val implementations = languageServer.textDocumentService.implementation(implementationParams(file, 6, 16)).get().left

        assertThat(implementations, hasSize(1))
    }

    @Test
    fun `find implementations of interface method`() {
        // Cursor on "speak" method in Animal interface (line 2, col 9)
        val implementations = languageServer.textDocumentService.implementation(implementationParams(file, 2, 9)).get().left

        assertThat(implementations, hasSize(2))
    }

    @Test
    fun `find implementations of interface property`() {
        // Cursor on "name" property in Animal interface (line 3, col 9)
        val implementations = languageServer.textDocumentService.implementation(implementationParams(file, 3, 9)).get().left

        assertThat(implementations, hasSize(2))
    }
}
