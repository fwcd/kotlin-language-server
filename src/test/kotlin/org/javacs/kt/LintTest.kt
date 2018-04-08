package org.javacs.kt

import org.eclipse.lsp4j.Diagnostic
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Test

class LintTest: LanguageServerTestFixture("lint") {
    @Test fun `report error on open`() {
        open("LintErrors.kt")

        assertThat(diagnostics, not(empty<Diagnostic>()))
    }
}