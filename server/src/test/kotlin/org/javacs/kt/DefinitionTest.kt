package org.javacs.kt

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasProperty
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertThat
import org.junit.Test

class DefinitionTest : SingleFileTestFixture("definition", "GoFrom.kt") {

    @Test
    fun `go to a definition in the same file`() {
        val definitions = languageServer.textDocumentService.definition(definitionParams(file, 3, 24)).get().left
        val uris = definitions.map { it.uri }

        assertThat(definitions, hasSize(1))
        assertThat(uris, hasItem(containsString("GoFrom.kt")))
    }

    @Test
    fun `go to a definition in a different file`() {
        val definitions = languageServer.textDocumentService.definition(definitionParams(file, 4, 24)).get().left
        val uris = definitions.map { it.uri }

        assertThat(definitions, hasSize(1))
        assertThat(uris, hasItem(containsString("GoTo.kt")))
    }
}


class GoToDefinitionOfPropertiesTest : SingleFileTestFixture("definition", "GoToProperties.kt") {

    @Test
    fun `go to definition of object property`() {
        assertGoToProperty(
            of = position(15, 20),
            expect = range(4, 15, 4, 32)
        )
    }

    @Test
    fun `go to definition of top level property`() {
        assertGoToProperty(
            of = position(17, 20),
            expect = range(11, 11, 11, 23)
        )
    }

    @Test
    fun `go to definition of class level property`() {
        assertGoToProperty(
            of = position(16, 20),
            expect = range(8, 9, 8, 25)
        )
    }

    @Test
    fun `go to definition of local property`() {
        assertGoToProperty(
            of = position(18, 18),
            expect = range(14, 9, 14, 20)
        )
    }

    private fun assertGoToProperty(of: Position, expect: Range) {
        val definitions = languageServer.textDocumentService.definition(definitionParams(file, of)).get().left
        val uris = definitions.map { it.uri }
        val ranges = definitions.map { it.range }

        assertThat(definitions, hasSize(1))
        assertThat(uris, hasItem(containsString(file)))
        assertThat(ranges, hasItem(equalTo(expect)))
    }
}
