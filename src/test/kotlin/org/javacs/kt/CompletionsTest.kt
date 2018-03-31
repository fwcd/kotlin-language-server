package org.javacs.kt

import org.hamcrest.Matchers.hasItem
import org.junit.Assert.assertThat
import org.junit.Test

class CompletionsTest: LanguageServerTestFixture("completions") {

    @Test
    fun `complete instance member`() {
        val file = "InstanceMember.kt"
        open(file)

        val completions = languageServer.textDocumentService.completion(position(file, 3, 15)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("instanceFoo"))
    }

    @Test
    fun `complete object member`() {
        val file = "ObjectMember.kt"
        open(file)

        val completions = languageServer.textDocumentService.completion(position(file, 2, 17)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("objectFoo"))
    }

    @Test
    fun `complete identifiers in function scope`() {
        val file = "FunctionScope.kt"
        open(file)

        val completions = languageServer.textDocumentService.completion(position(file, 4, 10)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("anArgument"))
        assertThat(labels, hasItem("aLocal"))
        assertThat(labels, hasItem("aClassVal"))
        assertThat(labels, hasItem("aClassFun"))
        assertThat(labels, hasItem("aCompanionVal"))
        assertThat(labels, hasItem("aCompanionFun"))
    }

    @Test
    fun `complete a type name`() {
        val file = "Types.kt"
        open(file)

        val completions = languageServer.textDocumentService.completion(position(file, 2, 25)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("SomeInnerClass"))
        assertThat(labels, hasItem("String"))
        assertThat(labels, hasItem("SomeInnerObject"))
        assertThat(labels, hasItem("SomeAlias"))
    }
}