package org.javacs.kt.diagnostic

import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Diagnostic as LangServerDiagnostic
import org.eclipse.lsp4j.DiagnosticTag
import org.javacs.kt.position.range
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.Diagnostic as KotlinDiagnostic
import java.nio.file.Path
import java.net.URI

fun convertDiagnostic(diagnostic: KotlinDiagnostic): List<Pair<URI, LangServerDiagnostic>> {
    val uri = diagnostic.psiFile.toPath().toUri()
    val content = diagnostic.psiFile.text

    return diagnostic.textRanges.map {
        val d = LangServerDiagnostic(
            range(content, it),
            message(diagnostic),
            severity(diagnostic.severity),
            "kotlin",
            code(diagnostic)
        ).apply {
            val factoryName = diagnostic.factory.name
            tags = mutableListOf<DiagnosticTag>()

            if ("UNUSED_"     in factoryName) tags.add(DiagnosticTag.Unnecessary)
            if ("DEPRECATION" in factoryName) tags.add(DiagnosticTag.Deprecated)
        }
        Pair(uri, d)
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

