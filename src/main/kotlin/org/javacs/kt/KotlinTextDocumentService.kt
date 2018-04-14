package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.javacs.kt.completion.completions
import org.javacs.kt.definition.goToDefinition
import org.javacs.kt.hover.hoverAt
import org.javacs.kt.position.offset
import org.javacs.kt.references.findReferences
import org.javacs.kt.signatureHelp.signatureHelpAt
import org.javacs.kt.symbols.documentSymbols
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class KotlinTextDocumentService(private val sf: SourceFiles, private val sp: SourcePath) : TextDocumentService {

    private fun recover(position: TextDocumentPositionParams): CompiledCode {
        val file = Paths.get(URI.create(position.textDocument.uri))
        val content = sp.content(file)
        val offset = offset(content, position.position.line, position.position.character)

        return sp.compiledCode(file, offset)
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Command>> {
        TODO("not implemented")
    }

    override fun hover(position: TextDocumentPositionParams): CompletableFuture<Hover?> {
        reportTime {
            LOG.info("Hovering at ${position.textDocument.uri} ${position.position.line}:${position.position.character}")

            val recover = recover(position)
            val hover = hoverAt(recover) ?: return noHover(position)

            return CompletableFuture.completedFuture(hover)
        }
    }

    private fun noHover(position: TextDocumentPositionParams): CompletableFuture<Hover?> {
        LOG.info("No hover found at ${describePosition(position)}")

        return CompletableFuture.completedFuture(null)
    }

    override fun documentHighlight(position: TextDocumentPositionParams): CompletableFuture<List<DocumentHighlight>> {
        TODO("not implemented")
    }

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> {
        TODO("not implemented")
    }

    override fun definition(position: TextDocumentPositionParams): CompletableFuture<List<Location>> {
        reportTime {
            LOG.info("Go-to-definition at ${describePosition(position)}")
            
            val recover = recover(position)
            val location = goToDefinition(recover) ?: return noDefinition(position)

            return CompletableFuture.completedFuture(listOf(location))
        }
    }

    private fun<T> noDefinition(position: TextDocumentPositionParams): CompletableFuture<T> {
        LOG.info("Couldn't find definition at ${describePosition(position)}")

        return CompletableFuture.completedFuture(null)
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

    override fun completion(position: TextDocumentPositionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        reportTime {
            LOG.info("Completing at ${describePosition(position)}")

            val recover = recover(position)
            val completions = completions(recover)

            LOG.info("Found ${completions.items.size} items")

            return CompletableFuture.completedFuture(Either.forRight(completions))
        }
    }

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        TODO("not implemented")
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<SymbolInformation>> {
        LOG.info("Find symbols in ${params.textDocument}")

        reportTime {
            val path = Paths.get(URI(params.textDocument.uri))
            val file = sp.parsedFile(path)
            val infos = documentSymbols(file)

            return CompletableFuture.completedFuture(infos)
        }
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val file = Paths.get(URI.create(params.textDocument.uri))

        sf.open(file, params.textDocument.text, params.textDocument.version)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }

    override fun signatureHelp(position: TextDocumentPositionParams): CompletableFuture<SignatureHelp?> {
        reportTime {
            LOG.info("Signature help at ${describePosition(position)}")

            val recover = recover(position)
            val result = signatureHelpAt(recover) ?: return noFunctionCall(position)

            return CompletableFuture.completedFuture(result)
        }
    }

    private fun<T> noFunctionCall(position: TextDocumentPositionParams): CompletableFuture<T?> {
        LOG.info("No function call around ${describePosition(position)}")

        return CompletableFuture.completedFuture(null)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val file = Paths.get(URI.create(params.textDocument.uri))

        sf.close(file)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        TODO("not implemented")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        sf.edit(params)
    }

    override fun references(position: ReferenceParams): CompletableFuture<List<Location>> {
        val file = Paths.get(URI.create(position.textDocument.uri))
        val content = sp.content(file)
        val offset = offset(content, position.position.line, position.position.character)
        val found = findReferences(file, offset, sp)

        return CompletableFuture.completedFuture(found)
    }

    override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
        TODO("not implemented")
    }

    private fun describePosition(position: TextDocumentPositionParams) =
            "${position.textDocument.uri} ${position.position.line}:${position.position.character}"
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