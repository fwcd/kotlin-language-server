package org.javacs.kt

import org.eclipse.lsp4j.Diagnostic
import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Test

class LintTest: SingleFileTestFixture("lint", "LintErrors.kt") {
    @Test fun `report error on open`() {
        languageServer.sourcePath.debounceLint.waitForPendingTask()

        assertThat(diagnostics, not(empty<Diagnostic>()))
    }

    @Test fun `only lint once for many edits in a short period`() {
        var text = "1"
        for (i in 1..10) {
            val newText = text + "1"

            replace(file, 3, 16, text, newText)
            text = newText
        }

        languageServer.sourcePath.debounceLint.waitForPendingTask()

        assertThat(diagnostics, not(empty<Diagnostic>()))
        assertThat(languageServer.sourcePath.lintCount, lessThan(3))
    }
}