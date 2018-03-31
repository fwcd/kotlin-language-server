package org.javacs.kt

import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Test

class CompletionsTest: LanguageServerTestFixture("completions") {

    @Test
    fun `complete instance member`() {
        val file = "InstanceMember.kt"
        open(file)

        val completions = languageServer.textDocumentService.completion(position(file, 3, 15)).get().right!!

        assertThat(completions.items, hasItem(hasProperty("label", equalTo("instanceFoo"))))
    }

    @Test
    fun `complete object member`() {
        val file = "ObjectMember.kt"
        open(file)

        val completions = languageServer.textDocumentService.completion(position(file, 2, 17)).get().right!!

        assertThat(completions.items, hasItem(hasProperty("label", equalTo("objectFoo"))))
    }
}