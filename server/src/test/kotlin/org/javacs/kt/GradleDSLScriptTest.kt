package org.javacs.kt

import org.junit.Test
import org.junit.Assert.assertThat
import org.hamcrest.Matchers.*

class GradleDSLScriptTest : SingleFileTestFixture("kotlinDSLWorkspace", "build.gradle.kts", Configuration().apply {
    scripts.enabled = true
    scripts.buildScriptsEnabled = true
}) {
    @Test fun `edit repositories`() {
        val completions = languageServer.textDocumentService.completion(completionParams(file, 7, 13)).get().right!!
        val labels = completions.items.map { it.label }

        assertThat(labels, hasItem("repositories"))
    }

    @Test fun `hover plugin`() {
        val hover = languageServer.textDocumentService.hover(hoverParams(file, 4, 8)).get()!!
        val contents = hover.contents.right

        assertThat(contents.value, containsString("fun PluginDependenciesSpec.kotlin(module: String): PluginDependencySpec"))
    }
}
