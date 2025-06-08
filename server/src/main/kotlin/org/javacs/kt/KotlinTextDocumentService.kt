package org.javacs.kt

import java.io.Closeable
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.javacs.kt.codeaction.codeActions
import org.javacs.kt.completion.completions
import org.javacs.kt.definition.DefinitionHandler
import org.javacs.kt.diagnostic.convertDiagnostic
import org.javacs.kt.formatting.FormattingService
import org.javacs.kt.highlight.documentHighlightsAt
import org.javacs.kt.hover.hoverAt
import org.javacs.kt.inlayhints.provideHints
import org.javacs.kt.position.extractRange
import org.javacs.kt.position.offset
import org.javacs.kt.position.position
import org.javacs.kt.references.findReferences
import org.javacs.kt.rename.renameSymbol
import org.javacs.kt.semantictokens.encodedSemanticTokens
import org.javacs.kt.signaturehelp.fetchSignatureHelpAt
import org.javacs.kt.symbols.documentSymbols
import org.javacs.kt.util.AsyncExecutor
import org.javacs.kt.util.Debouncer
import org.javacs.kt.util.TemporaryDirectory
import org.javacs.kt.util.describeURI
import org.javacs.kt.util.describeURIs
import org.javacs.kt.util.filePath
import org.javacs.kt.util.noResult
import org.javacs.kt.util.parseURI
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics

class KotlinTextDocumentService(
    private val sourceFiles: SourceFiles,
    private val sourcePath: SourcePath,
    private val config: Configuration,
    tempDirectory: TemporaryDirectory,
    uriContentProvider: URIContentProvider,
    cp: CompilerClassPath,
) : TextDocumentService, Closeable {
    private lateinit var client: LanguageClient
    private val asyncExecutor = AsyncExecutor(name = "KotlinTextDocumentService")
    private val formattingService = FormattingService(config.formatting)
    private val definitionHandler =
        DefinitionHandler(
            uriContentProvider.classContentProvider,
            tempDirectory,
            config.externalSources,
            cp,
        )

    var lintDebouncer = Debouncer(Duration.ofMillis(config.diagnostics.debounceTime))
    val lintTodo = mutableSetOf<URI>()
    var lintCount = 0

    var lintRecompilationCallback: () -> Unit
        get() = sourcePath.beforeCompileCallback
        set(callback) {
            sourcePath.beforeCompileCallback = callback
        }

    private val TextDocumentIdentifier.filePath: Path?
        get() = parseURI(uri).filePath

    private val TextDocumentIdentifier.content: String
        get() = sourcePath.content(parseURI(uri))

    fun connect(client: LanguageClient) {
        this.client = client
    }

    private enum class Recompile {
        ALWAYS,
        AFTER_DOT,
        NEVER,
    }

    private fun recover(
        position: TextDocumentPositionParams,
        recompile: Recompile = Recompile.NEVER,
    ): Pair<CompiledFile, Int>? {
        return recover(position.textDocument.uri, position.position, recompile)
    }

    private fun recover(
        uriString: String,
        position: Position,
        recompile: Recompile,
    ): Pair<CompiledFile, Int>? {
        val uri = parseURI(uriString)
        if (!sourceFiles.isIncluded(uri)) {
            LOG.warn("URI is excluded, therefore cannot be recovered: $uri")
            return null
        }
        val content = sourcePath.content(uri)
        val offset = offset(content, position.line, position.character)
        val shouldRecompile =
            when (recompile) {
                Recompile.ALWAYS -> true
                Recompile.AFTER_DOT -> offset > 0 && content[offset - 1] == '.'
                Recompile.NEVER -> false
            }
        val compiled =
            if (shouldRecompile) sourcePath.currentVersion(uri)
            else sourcePath.latestCompiledVersion(uri)
        return Pair(compiled, offset)
    }

    override fun codeAction(
        params: CodeActionParams
    ): CompletableFuture<List<Either<Command, CodeAction>>> =
        asyncExecutor.compute {
            val (file, _) =
                recover(params.textDocument.uri, params.range.start, Recompile.NEVER)
                    ?: return@compute emptyList()
            codeActions(file, sourcePath.index, params.range, params.context)
        }

    override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> =
        asyncExecutor.compute {
            val (file, _) =
                recover(params.textDocument.uri, params.range.start, Recompile.ALWAYS)
                    ?: return@compute emptyList()
            provideHints(file, config.inlayHints)
        }

    override fun hover(position: HoverParams): CompletableFuture<Hover?> =
        asyncExecutor.compute {
            reportTime {
                LOG.info("Hovering at {}", describePosition(position))

                val (file, cursor) = recover(position) ?: return@compute null
                hoverAt(file, cursor)
                    ?: noResult("No hover found at ${describePosition(position)}", null)
            }
        }

    override fun documentHighlight(
        position: DocumentHighlightParams
    ): CompletableFuture<List<DocumentHighlight>> =
        asyncExecutor.compute {
            val (file, cursor) =
                recover(position.textDocument.uri, position.position, Recompile.NEVER)
                    ?: return@compute emptyList()
            documentHighlightsAt(file, cursor)
        }

    override fun onTypeFormatting(
        params: DocumentOnTypeFormattingParams
    ): CompletableFuture<List<TextEdit>> {
        TODO("not implemented")
    }

    override fun definition(
        position: DefinitionParams
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        asyncExecutor.compute {
            reportTime {
                LOG.info("Go-to-definition at {}", describePosition(position))

                val (file, cursor) = recover(position) ?: return@compute Either.forLeft(emptyList())
                definitionHandler.goToDefinition(file, cursor)?.let(::listOf)?.let {
                    Either.forLeft(it)
                }
                    ?: noResult(
                        "Couldn't find definition at ${describePosition(position)}",
                        Either.forLeft(emptyList()),
                    )
            }
        }

    override fun rangeFormatting(
        params: DocumentRangeFormattingParams
    ): CompletableFuture<List<TextEdit>> =
        asyncExecutor.compute {
            val code = extractRange(params.textDocument.content, params.range)
            listOf(TextEdit(params.range, formattingService.formatKotlinCode(code, params.options)))
        }

    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> {
        TODO("not implemented")
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit?> =
        asyncExecutor.compute {
            val (file, cursor) = recover(params, Recompile.NEVER) ?: return@compute null
            renameSymbol(file, cursor, sourcePath, params.newName)
        }

    override fun completion(
        position: CompletionParams
    ): CompletableFuture<Either<List<CompletionItem>, CompletionList>> =
        asyncExecutor.compute {
            reportTime {
                LOG.info("Completing at {}", describePosition(position))

                val (file, cursor) =
                    recover(position)
                        ?: return@compute Either.forRight(
                            CompletionList()
                        ) // TODO: Investigate when to recompile
                val completions = completions(file, cursor, sourcePath.index, config.completion)
                LOG.info("Found {} items", completions.items.size)

                Either.forRight(completions)
            }
        }

    override fun resolveCompletionItem(
        unresolved: CompletionItem
    ): CompletableFuture<CompletionItem> {
        TODO("not implemented")
    }

    override fun documentSymbol(
        params: DocumentSymbolParams
    ): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> =
        asyncExecutor.compute {
            LOG.info("Find symbols in {}", describeURI(params.textDocument.uri))

            reportTime {
                val uri = parseURI(params.textDocument.uri)
                val parsed = sourcePath.parsedFile(uri)

                documentSymbols(parsed)
            }
        }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = parseURI(params.textDocument.uri)
        sourceFiles.open(uri, params.textDocument.text, params.textDocument.version)
        lintNow(uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // Lint after saving to prevent inconsistent diagnostics
        val uri = parseURI(params.textDocument.uri)
        lintNow(uri)
        lintDebouncer.schedule { sourcePath.save(uri) }
    }

    override fun signatureHelp(position: SignatureHelpParams): CompletableFuture<SignatureHelp?> =
        asyncExecutor.compute {
            reportTime {
                LOG.info("Signature help at {}", describePosition(position))

                val (file, cursor) = recover(position) ?: return@compute null
                fetchSignatureHelpAt(file, cursor)
                    ?: noResult("No function call around ${describePosition(position)}", null)
            }
        }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = parseURI(params.textDocument.uri)
        sourceFiles.close(uri)
        clearDiagnostics(uri)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> =
        asyncExecutor.compute {
            val code = params.textDocument.content
            LOG.info("Formatting {}", describeURI(params.textDocument.uri))
            listOf(
                TextEdit(
                    Range(Position(0, 0), position(code, code.length)),
                    formattingService.formatKotlinCode(code, params.options),
                )
            )
        }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = parseURI(params.textDocument.uri)
        sourceFiles.edit(uri, params.textDocument.version, params.contentChanges)
        lintLater(uri)
    }

    override fun references(position: ReferenceParams) =
        asyncExecutor.compute {
            position.textDocument.filePath?.let { file ->
                val content = sourcePath.content(parseURI(position.textDocument.uri))
                val offset = offset(content, position.position.line, position.position.character)
                findReferences(file, offset, sourcePath)
            }
        }

    override fun semanticTokensFull(params: SemanticTokensParams) =
        asyncExecutor.compute {
            LOG.info("Full semantic tokens in {}", describeURI(params.textDocument.uri))

            reportTime {
                val uri = parseURI(params.textDocument.uri)
                val file = sourcePath.currentVersion(uri)

                val tokens = encodedSemanticTokens(file)
                LOG.info("Found {} tokens", tokens.size)

                SemanticTokens(tokens)
            }
        }

    override fun semanticTokensRange(params: SemanticTokensRangeParams) =
        asyncExecutor.compute {
            LOG.info("Ranged semantic tokens in {}", describeURI(params.textDocument.uri))

            reportTime {
                val uri = parseURI(params.textDocument.uri)
                val file = sourcePath.currentVersion(uri)

                val tokens = encodedSemanticTokens(file, params.range)
                LOG.info("Found {} tokens", tokens.size)

                SemanticTokens(tokens)
            }
        }

    override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
        TODO("not implemented")
    }

    private fun describePosition(position: TextDocumentPositionParams): String {
        return "${describeURI(position.textDocument.uri)} ${position.position.line + 1}:${position.position.character + 1}"
    }

    fun updateDebouncer() {
        lintDebouncer = Debouncer(Duration.ofMillis(config.diagnostics.debounceTime))
    }

    fun lintAll() {
        lintDebouncer.submitImmediately {
            sourcePath.compileAllFiles()
            sourcePath.saveAllFiles()
            sourcePath.refreshDependencyIndexes()
        }
    }

    private fun clearLint(): List<URI> {
        val result = lintTodo.toList()
        lintTodo.clear()
        return result
    }

    private fun lintLater(uri: URI) {
        lintTodo.add(uri)
        lintDebouncer.schedule(::doLint)
    }

    private fun lintNow(file: URI) {
        lintTodo.add(file)
        lintDebouncer.submitImmediately(::doLint)
    }

    private fun doLint(cancelCallback: () -> Boolean) {
        LOG.info("Linting {}", describeURIs(lintTodo))
        val files = clearLint()
        val context = sourcePath.compileFiles(files)
        if (!cancelCallback.invoke()) {
            reportDiagnostics(files, context.diagnostics)
        }
        lintCount++
    }

    private fun reportDiagnostics(compiled: Collection<URI>, kotlinDiagnostics: Diagnostics) {
        val langServerDiagnostics =
            kotlinDiagnostics.flatMap(::convertDiagnostic).filter {
                config.diagnostics.enabled && it.second.severity <= config.diagnostics.level
            }
        val byFile = langServerDiagnostics.groupBy({ it.first }, { it.second })

        for ((uri, diagnostics) in byFile) {
            if (sourceFiles.isOpen(uri)) {
                client.publishDiagnostics(PublishDiagnosticsParams(uri.toString(), diagnostics))

                LOG.info("Reported {} diagnostics in {}", diagnostics.size, describeURI(uri))
            } else
                LOG.info(
                    "Ignore {} diagnostics in {} because it's not open",
                    diagnostics.size,
                    describeURI(uri),
                )
        }

        val noErrors = compiled - byFile.keys
        for (file in noErrors) {
            clearDiagnostics(file)

            LOG.info("No diagnostics in {}", file)
        }

        lintCount++
    }

    private fun clearDiagnostics(uri: URI) {
        client.publishDiagnostics(PublishDiagnosticsParams(uri.toString(), listOf()))
    }

    private fun shutdownExecutors(awaitTermination: Boolean = true) {
        asyncExecutor.shutdown(awaitTermination)
        lintDebouncer.shutdown(awaitTermination)
    }

    override fun close() {
        shutdownExecutors()
    }
}

private inline fun <T> reportTime(block: () -> T): T {
    val started = System.currentTimeMillis()
    try {
        return block()
    } finally {
        val finished = System.currentTimeMillis()
        LOG.info("Finished in {} ms", finished - started)
    }
}
