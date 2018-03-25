package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.util.concurrent.CompletableFuture

class KotlinTextDocumentService : TextDocumentService {
    private val activeDocuments = HashMap<URI, VersionedContent>()

    data class VersionedContent(val content: String, val version: Int)

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        TODO("not implemented")
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<MutableList<out Command>> {
        TODO("not implemented")
    }

    override fun hover(position: TextDocumentPositionParams): CompletableFuture<Hover> {
        TODO("not implemented")
    }

    override fun documentHighlight(position: TextDocumentPositionParams): CompletableFuture<MutableList<out DocumentHighlight>> {
        TODO("not implemented")
    }

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        TODO("not implemented")
    }

    override fun definition(position: TextDocumentPositionParams): CompletableFuture<MutableList<out Location>> {
        TODO("not implemented")
    }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        TODO("not implemented")
    }

    override fun codeLens(params: CodeLensParams): CompletableFuture<MutableList<out CodeLens>> {
        TODO("not implemented")
    }

    override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
        TODO("not implemented")
    }

    override fun completion(position: TextDocumentPositionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
        TODO("not implemented")
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<MutableList<out SymbolInformation>> {
        TODO("not implemented")
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        activeDocuments[URI(params.textDocument.uri)] = VersionedContent(params.textDocument.text, params.textDocument.version)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }

    override fun signatureHelp(position: TextDocumentPositionParams): CompletableFuture<SignatureHelp> {
        TODO("not implemented")
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        activeDocuments.remove(URI(params.textDocument.uri))
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        TODO("not implemented")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val document = params.textDocument
        val uri = URI.create(document.uri)
        val existing = activeDocuments[uri]!!
        var newText = existing.content

        if (document.version > existing.version) {
            for (change in params.contentChanges) {
                if (change.range == null)
                    activeDocuments[uri] = VersionedContent(change.text, document.version)
                else
                    newText = patch(newText, change)
            }

            activeDocuments[uri] = VersionedContent(newText, document.version)
        }
        else LOG.warning("""Ignored change with version ${document.version} <= ${existing.version}""")
    }

    override fun references(params: ReferenceParams): CompletableFuture<MutableList<out Location>> {
        TODO("not implemented")
    }

    override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
        TODO("not implemented")
    }

    private fun patch(sourceText: String, change: TextDocumentContentChangeEvent): String {
        try {
            val range = change.range
            val reader = BufferedReader(StringReader(sourceText))
            val writer = StringWriter()

            // Skip unchanged lines
            var line = 0

            while (line < range.start.line) {
                writer.write(reader.readLine() + '\n')
                line++
            }

            // Skip unchanged chars
            for (character in 0 until range.start.character) writer.write(reader.read())

            // Write replacement text
            writer.write(change.text)

            // Skip replaced text
            reader.skip(change.rangeLength!!.toLong())

            // Write remaining text
            while (true) {
                val next = reader.read()

                if (next == -1) return writer.toString()
                else writer.write(next)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}