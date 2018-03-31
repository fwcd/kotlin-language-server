package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.util.concurrent.CompletableFuture

private const val MAX_COMPLETION_ITEMS = 50

class KotlinTextDocumentService : TextDocumentService {
    private val activeDocuments = hashMapOf<URI, ActiveDocument>()

    fun didReAnalyze(document: URI): Boolean {
        return activeDocuments[document]?.compiled?.reAnalyzed ?: false
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<MutableList<out Command>> {
        TODO("not implemented")
    }

    override fun hover(position: TextDocumentPositionParams): CompletableFuture<Hover?> {
        LOG.info("Looking for hover at ${position.textDocument.uri} ${position.position.line}:${position.position.character}")

        val uri = URI(position.textDocument.uri)
        val active = activeDocuments[uri] ?: throw RuntimeException("$uri is not open")
        val offset = offset(active.content, position.position.line, position.position.character)
        val recover = active.compiled.recover(active.content, offset) ?: return cantRecover(position)
        val (location, decl) = recover.hover() ?: return noHover(position)
        val hoverText = DECL_RENDERER.render(decl)
        val hover = Either.forRight<String, MarkedString>(MarkedString("kotlin", hoverText))
        val range = Range(position(active.content, location.startOffset), position(active.content, location.endOffset))

        return CompletableFuture.completedFuture(Hover(listOf(hover), range))
    }

    private fun noHover(position: TextDocumentPositionParams): CompletableFuture<Hover?> {
        LOG.info("No hover found at ${describePosition(position)}")

        return CompletableFuture.completedFuture(null)
    }

    private fun describePosition(position: TextDocumentPositionParams) =
            "${position.textDocument.uri} ${position.position.line}:${position.position.character}"

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
        val uri = URI(position.textDocument.uri)
        val active = activeDocuments[uri] ?: throw RuntimeException("$uri is not open")
        val offset = offset(active.content, position.position.line, position.position.character)
        val recover = active.compiled.recover(active.content, offset) ?: return cantRecover(position)
        val completions = recover.completions()
        val list = completions.map(::completionItem).take(MAX_COMPLETION_ITEMS).toList()
        val isIncomplete = list.size == MAX_COMPLETION_ITEMS

        return CompletableFuture.completedFuture(Either.forRight(CompletionList(isIncomplete, list)))
    }

    private fun<T> cantRecover(position: TextDocumentPositionParams): CompletableFuture<T> {
        LOG.info("Couldn't recover compiler at ${describePosition(position)}")

        return CompletableFuture.completedFuture(null)
    }

    private fun completionItem(desc: DeclarationDescriptor): CompletionItem =
            desc.accept(RenderCompletionItem(), null)

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        TODO("not implemented")
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<MutableList<out SymbolInformation>> {
        TODO("not implemented")
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = URI(params.textDocument.uri)
        val compiled = LiveFile(uri, params.textDocument.text)
        activeDocuments[uri] = ActiveDocument(params.textDocument.text, params.textDocument.version, compiled)
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
                    activeDocuments[uri] = ActiveDocument(change.text, document.version, existing.compiled)
                else
                    newText = patch(newText, change)
            }

            activeDocuments[uri] = ActiveDocument(newText, document.version, existing.compiled)
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

    /**
     * Convert from 0-based line and column to 0-based offset
     */
    private fun offset(content: String, line: Int, char: Int): Int {
        val reader = content.reader()
        var offset = 0

        var lineOffset = 0
        while (lineOffset < line) {
            val nextChar = reader.read()

            if (nextChar == -1)
                throw RuntimeException("Reached end of file before reaching line $line")

            if (nextChar.toChar() == '\n')
                lineOffset++

            offset++
        }

        var charOffset = 0
        while (charOffset < char) {
            val nextChar = reader.read()

            if (nextChar == -1)
                throw RuntimeException("Reached end of file before reaching char $char")

            charOffset++
            offset++
        }

        return offset
    }

    private fun position(content: String, offset: Int): Position {
        val reader = content.reader()
        var line = 0
        var char = 0

        var find = 0
        while (find < offset) {
            val nextChar = reader.read()

            if (nextChar == -1)
                throw RuntimeException("Reached end of file before reaching offset $offset")

            find++
            char++

            if (nextChar.toChar() == '\n') {
                line++
                char = 0
            }
        }

        return Position(line, char)
    }

    data class ActiveDocument(val content: String, val version: Int, val compiled: LiveFile)
}

