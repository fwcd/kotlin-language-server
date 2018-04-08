package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.DiagnosticSeverity
import org.javacs.kt.position.range
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import java.nio.file.Path
import java.nio.file.Paths

typealias LangServerDiagnostic = org.eclipse.lsp4j.Diagnostic
typealias KotlinDiagnostic = org.jetbrains.kotlin.diagnostics.Diagnostic

fun langServerDiagnostic(diagnostic: KotlinDiagnostic, openFiles: (Path) -> String): List<LangServerDiagnostic> {
    return diagnostic.textRanges.map {
        val path = diagnostic.psiFile.originalFile.viewProvider.virtualFile.path
        val content = openFiles(Paths.get(path))

        LangServerDiagnostic(
                range(content, it),
                message(diagnostic),
                severity(diagnostic.severity),
                "kotlin",
                code(diagnostic))
    }
}

private fun code(diagnostic: KotlinDiagnostic) =
        diagnostic.factory.name

private fun message(diagnostic: KotlinDiagnostic) =
        DefaultErrorMessages.render(diagnostic)

private fun severity(severity: Severity): DiagnosticSeverity =
        when (severity) {
            Severity.INFO -> DiagnosticSeverity.Information
            Severity.ERROR -> DiagnosticSeverity.Error
            Severity.WARNING -> DiagnosticSeverity.Warning
        }
