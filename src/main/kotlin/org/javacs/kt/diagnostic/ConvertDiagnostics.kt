package org.javacs.kt.diagnostic

import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.DiagnosticSeverity
import org.javacs.kt.position.range
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import java.nio.file.Path
import java.nio.file.Paths

typealias LangServerDiagnostic = org.eclipse.lsp4j.Diagnostic
typealias KotlinDiagnostic = org.jetbrains.kotlin.diagnostics.Diagnostic

class ConvertDiagnostics(private val openFiles: (Path) -> String?) {
    fun convert(diagnostic: KotlinDiagnostic): List<Pair<Path, LangServerDiagnostic>> {
        return diagnostic.textRanges.map {
            val path = diagnostic.psiFile.toPath()
            val content = openFiles(path) ?: return emptyList()
            val result = LangServerDiagnostic(
                    range(content, it),
                    message(diagnostic),
                    severity(diagnostic.severity),
                    "kotlin",
                    code(diagnostic))
            Pair(path, result)
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
}

fun PsiFile.toPath() =
        Paths.get(this.originalFile.viewProvider.virtualFile.path)

