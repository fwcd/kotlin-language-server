package org.javacs.kt.codeaction.quickfix

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.jetbrains.kotlin.psi.KtFile
import org.javacs.kt.CompiledFile
import org.javacs.kt.LOG
import org.javacs.kt.index.SymbolIndex
import org.javacs.kt.index.Symbol
import org.javacs.kt.position.offset
import org.javacs.kt.util.toPath
import org.javacs.kt.codeaction.quickfix.diagnosticMatch
import org.javacs.kt.imports.getImportTextEditEntry

class AddMissingImportsQuickFix: QuickFix {
    override fun compute(file: CompiledFile, index: SymbolIndex, range: Range, diagnostics: List<Diagnostic>): List<Either<Command, CodeAction>> {
        val uri = file.parse.toPath().toUri().toString()
        val unresolvedReferences = getUnresolvedReferencesFromDiagnostics(diagnostics) 
        
        return unresolvedReferences.flatMap { diagnostic ->
            val diagnosticRange = diagnostic.range
            val startCursor = offset(file.content, diagnosticRange.start)
            val endCursor = offset(file.content, diagnosticRange.end)
            val symbolName = file.content.substring(startCursor, endCursor)

            getImportAlternatives(symbolName, file.parse, index).map { (importStr, edit) ->
                val codeAction = CodeAction()
                codeAction.title = "Import ${importStr}"
                codeAction.kind = CodeActionKind.QuickFix
                codeAction.diagnostics = listOf(diagnostic)
                codeAction.edit = WorkspaceEdit(mapOf(uri to listOf(edit)))
                
                Either.forRight(codeAction)
            }
        }
    }

    private fun getUnresolvedReferencesFromDiagnostics(diagnostics: List<Diagnostic>): List<Diagnostic> =
        diagnostics.filter {
            "UNRESOLVED_REFERENCE" == it.code.left.trim()
        }

    private fun getImportAlternatives(symbolName: String, file: KtFile, index: SymbolIndex): List<Pair<String, TextEdit>> {
        // wildcard matcher to empty string, because we only want to match exactly the symbol itself, not anything extra
        val queryResult = index.query(symbolName, suffix = "")
        
        return queryResult
            .filter {
                it.kind != Symbol.Kind.MODULE &&
                // TODO: Visibility checker should be less liberal
                (it.visibility == Symbol.Visibility.PUBLIC
                 || it.visibility == Symbol.Visibility.PROTECTED
                 || it.visibility == Symbol.Visibility.INTERNAL)
            }
            .map {
                Pair(it.fqName.toString(), getImportTextEditEntry(file, it.fqName))
            }
    }
}
