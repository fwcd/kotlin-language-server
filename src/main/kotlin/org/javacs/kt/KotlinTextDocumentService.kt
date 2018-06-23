package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.javacs.kt.completion.*
import org.javacs.kt.definition.goToDefinition
import org.javacs.kt.diagnostic.convertDiagnostic
import org.javacs.kt.hover.hoverAt
import org.javacs.kt.position.offset
import org.javacs.kt.references.findReferences
import org.javacs.kt.signatureHelp.signatureHelpAt
import org.javacs.kt.symbols.documentSymbols
import org.javacs.kt.util.noResult
import org.javacs.kt.util.computeAsync
import org.javacs.kt.util.Debouncer
import org.javacs.kt.commands.JAVA_TO_KOTLIN_COMMAND
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.CompletableFuture

class KotlinTextDocumentService(private val sf: SourceFiles, private val sp: SourcePath) : TextDocumentService {
    private lateinit var client: LanguageClient

    fun connect(client: LanguageClient) {
        this.client = client
    }

    private val TextDocumentIdentifier.filePath
        get() = Paths.get(URI.create(uri))

    private val TextDocumentIdentifier.content
        get() = sp.content(filePath)

    private fun recover(position: TextDocumentPositionParams, recompile: Boolean): Pair<CompiledFile, Int> {
        val file = position.textDocument.filePath
        val content = sp.content(file)
        val offset = offset(content, position.position.line, position.position.character)
        val compiled = if (recompile) sp.currentVersion(file) else sp.latestCompiledVersion(file)
        return Pair(compiled, offset)
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Command>> = computeAsync {
        listOf(
            Command("Convert Java code to Kotlin", JAVA_TO_KOTLIN_COMMAND, listOf(
                params.textDocument.uri,
                params.range
            ))
        )
    }

    override fun hover(position: TextDocumentPositionParams): CompletableFuture<Hover?> = computeAsync {
        reportTime {
            LOG.info("Hovering at ${position.textDocument.uri} ${position.position.line}:${position.position.character}")

            val (file, cursor) = recover(position, true)
            hoverAt(file, cursor) ?: noResult("No hover found at ${describePosition(position)}", null)
        }
    }

    override fun documentHighlight(position: TextDocumentPositionParams): CompletableFuture<List<DocumentHighlight>> {
        TODO("not implemented")
    }

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> {
        TODO("not implemented")
    }

    override fun definition(position: TextDocumentPositionParams) = computeAsync {
        reportTime {
            LOG.info("Go-to-definition at ${describePosition(position)}")

            val (file, cursor) = recover(position, false)
            goToDefinition(file, cursor)?.let(::listOf) ?: noResult("Couldn't find definition at ${describePosition(position)}", emptyList())
        }
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> {
        TODO("not implemented")
    }

    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> {
        TODO("not implemented")
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
        TODO("not implemented")
    }

    override fun completion(position: CompletionParams) = computeAsync {
        reportTime {
            LOG.info("Completing at ${describePosition(position)}")

            val (file, cursor) = recover(position, false)
            val completions = completions(file, cursor)

            LOG.info("Found ${completions.items.size} items")

            Either.forRight<List<CompletionItem>, CompletionList>(completions)
        }
    }

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        TODO("not implemented")
    }

    override fun documentSymbol(params: DocumentSymbolParams) = computeAsync {
        LOG.info("Find symbols in ${params.textDocument}")

        reportTime {
            val path = params.textDocument.filePath
            val file = sp.parsedFile(path)
            documentSymbols(file)
        }
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val file = Paths.get(URI.create(params.textDocument.uri))

        sf.open(file, params.textDocument.text, params.textDocument.version)
        lintNow(file)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {}

    override fun signatureHelp(position: TextDocumentPositionParams): CompletableFuture<SignatureHelp?> = computeAsync {
        reportTime {
            LOG.info("Signature help at ${describePosition(position)}")

            val (file, cursor) = recover(position, false)
            signatureHelpAt(file, cursor) ?: noResult("No function call around ${describePosition(position)}", null)
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val file = params.textDocument.filePath

        sf.close(file)
        clearDiagnostics(file)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        TODO("not implemented")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val file = params.textDocument.filePath

        sf.edit(file,  params.textDocument.version, params.contentChanges)
        lintLater(file)
    }

    override fun references(position: ReferenceParams) = computeAsync {
        val file = Paths.get(URI.create(position.textDocument.uri))
        val content = sp.content(file)
        val offset = offset(content, position.position.line, position.position.character)
        findReferences(file, offset, sp)
    }

    override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
        TODO("not implemented")
    }

    private fun describePosition(position: TextDocumentPositionParams): String {
        val path = Paths.get(URI.create(position.textDocument.uri))
        return "${path.fileName} ${position.position.line + 1}:${position.position.character + 1}"
    }

    val debounceLint = Debouncer(Duration.ofMillis(250))
    val lintTodo = mutableSetOf<Path>()
    var lintCount = 0

    private fun clearLint(): List<Path> {
        val result = lintTodo.toList()
        lintTodo.clear()
        return result
    }

    private fun lintLater(file: Path) {
        lintTodo.add(file)
        debounceLint.submit(::doLint)
    }

    private fun lintNow(file: Path) {
        lintTodo.add(file)
        debounceLint.submitImmediately(::doLint)
    }

    private fun doLint() {
        LOG.info("Linting ${describeFiles(lintTodo)}")
        val files = clearLint()
        val context = sp.compileFiles(files)
        reportDiagnostics(files, context.diagnostics)
        lintCount++
    }

    private fun reportDiagnostics(compiled: Collection<Path>, kotlinDiagnostics: Diagnostics) {
        val langServerDiagnostics = kotlinDiagnostics.flatMap(::convertDiagnostic)
        val byFile = langServerDiagnostics.groupBy({ it.first }, { it.second })

        for ((file, diagnostics) in byFile) {
            if (sf.isOpen(file)) {
                client.publishDiagnostics(PublishDiagnosticsParams(file.toUri().toString(), diagnostics))

                LOG.info("Reported ${diagnostics.size} diagnostics in ${file.fileName}")
            }
            else LOG.info("Ignore ${diagnostics.size} diagnostics in ${file.fileName} because it's not open")
        }

        val noErrors = compiled - byFile.keys
        for (file in noErrors) {
            clearDiagnostics(file)

            LOG.info("No diagnostics in ${file.fileName}")
        }

        // LOG.log(Level.WARNING, "LINT", Exception())

        lintCount++
    }

    private fun clearDiagnostics(file: Path) {
        client.publishDiagnostics(PublishDiagnosticsParams(file.toUri().toString(), listOf()))
    }
}

private inline fun<T> reportTime(block: () -> T): T {
    val started = System.currentTimeMillis()
    try {
        return block()
    } finally {
        val finished = System.currentTimeMillis()
        LOG.info("Finished in ${finished - started} ms")
    }
}
