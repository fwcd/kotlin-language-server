package org.javacs.kt.codeaction

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledFile
import org.javacs.kt.codeaction.quickfix.ImplementAbstractFunctionsQuickFix
import org.javacs.kt.command.JAVA_TO_KOTLIN_COMMAND
import org.javacs.kt.util.toPath

val QUICK_FIXES = listOf(
    ImplementAbstractFunctionsQuickFix()
)

fun codeActions(file: CompiledFile, range: Range, context: CodeActionContext): List<Either<Command, CodeAction>> {
    val requestedKinds = context.only ?: listOf(CodeActionKind.Refactor)
    return requestedKinds.map {
        when (it) {
            CodeActionKind.Refactor -> getRefactors(file, range)
            CodeActionKind.QuickFix -> getQuickFixes(file, range, context.diagnostics)
            else -> listOf()
        }
    }.flatten()
}

fun getRefactors(file: CompiledFile, range: Range): List<Either<Command, CodeAction>> {
    val hasSelection = (range.end.line - range.start.line) != 0 || (range.end.character - range.start.character) != 0
    return if (hasSelection) {
        listOf(
            Either.forLeft<Command, CodeAction>(
                Command("Convert Java to Kotlin", JAVA_TO_KOTLIN_COMMAND, listOf(
                    file.parse.toPath().toUri().toString(),
                    range
                ))
            )
        )
    } else {
        emptyList()
    }
}

fun getQuickFixes(file: CompiledFile, range: Range, diagnostics: List<Diagnostic>): List<Either<Command, CodeAction>> {
    return QUICK_FIXES.mapNotNull {
        it.compute(file, range, diagnostics)
    }
}
