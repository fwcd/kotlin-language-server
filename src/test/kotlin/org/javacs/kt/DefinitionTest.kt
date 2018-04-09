package org.javacs.kt

import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test

class DefinitionTest: LanguageServerTestFixture("definition") {
    val file = "GoFrom.kt"

    @Before fun `open GoFrom`() {
        open(file)
    }

    @Test fun `go to a definition in the same file`() {
        val definitions = languageServer.textDocumentService.definition(position(file, 3, 24)).get()

        assertThat(definitions, hasSize(1))
        assertThat(definitions, hasItem(hasProperty("uri", containsString("GoFrom.kt"))))
    }

    @Test fun `go to a definition in a different file`() {
        val definitions = languageServer.textDocumentService.definition(position(file, 4, 24)).get()

        assertThat(definitions, hasSize(1))
        assertThat(definitions, hasItem(hasProperty("uri", containsString("GoTo.kt"))))
    }
}