package org.javacs.kt

import java.util.concurrent.CancellationException
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Test

class DiagnosticTest : SingleFileTestFixture("diagnostic", "Diagnostics.kt") {
    @Test fun `report diagnostics on open`() {
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertThat(diagnostics, hasSize(2))
        assertThat(errors, hasSize(1))
        assertThat(warnings, hasSize(1))
    }

    @Test fun `report only errors`() {
        languageServer.config.diagnostics.level = DiagnosticSeverity.Error

        // Trigger a diagnostics update via a dummy change.
        replace(file, 6, 1, "", " ")
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertThat(diagnostics, hasSize(1))
        assertThat(errors, hasSize(1))
        assertThat(warnings, empty<Diagnostic>())
    }

    @Test fun `disable diagnostics`() {
        languageServer.config.diagnostics.enabled = false

        // Trigger a diagnostics update via a dummy change.
        replace(file, 1, 1, "", " ")
        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertThat(diagnostics, empty<Diagnostic>())
    }

    @Test fun `only lint once for many edits in a short period`() {
        var text = "1"
        repeat(10) {
            val newText = text + "1"

            replace(file, 7, 16, text, newText)
            text = newText
        }

        languageServer.textDocumentService.debounceLint.waitForPendingTask()

        assertThat(diagnostics, not(empty<Diagnostic>()))
        assertThat(languageServer.textDocumentService.lintCount, lessThan(5))
    }

    @Test fun `linting should not be dropped if another linting is running`() {
        var callbackCount = 0
        languageServer.textDocumentService.debounceLint.waitForPendingTask()
        languageServer.textDocumentService.lintRecompilationCallback = {
            if (callbackCount++ == 0) {
                replace(file, 7, 9, "return 11", "")
                languageServer.textDocumentService.documentSymbol(DocumentSymbolParams(TextDocumentIdentifier(uri(file).toString()))).get()
            }
        }
        replace(file, 6, 9, "Foo()", "")
        replace(file, 7, 16, "", "1")

        while (callbackCount < 2) {
            try {
                languageServer.textDocumentService.debounceLint.waitForPendingTask()
            } catch (ex: CancellationException) {}
        }

        languageServer.textDocumentService.debounceLint.waitForPendingTask()
        assertThat(diagnostics, empty<Diagnostic>())
        languageServer.textDocumentService.lintRecompilationCallback = {}
    }
}
