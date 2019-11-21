package org.javacs.kt

import org.hamcrest.Matchers.hasItem
import org.junit.Assert.assertThat
import org.junit.Test

class ScriptTest : LanguageServerTestFixture("script") {
    @Test fun `open script`() {
        open("ExampleScript.kts")
    }
}

class EditFunctionTest : SingleFileTestFixture("script", "FunctionScript.kts") {
    @Test fun `edit a function in a script`() {
        replace("FunctionScript.kts", 3, 18, "2", "f")

        val completions = languageServer.textDocumentService.completion(completionParams(file, 3, 19)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("first"))
    }
}
