package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.javacs.kt.RecompileStrategy.*
import org.javacs.kt.RecompileStrategy.Function
import org.javacs.kt.docs.findDoc
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

private const val MAX_COMPLETION_ITEMS = 50

class KotlinTextDocumentService(private val workspace: KotlinWorkspaceService) : TextDocumentService {
    private val activeDocuments = hashMapOf<Path, ActiveDocument>()

    override fun codeAction(params: CodeActionParams): CompletableFuture<MutableList<out Command>> {
        TODO("not implemented")
    }

    override fun hover(position: TextDocumentPositionParams): CompletableFuture<Hover?> {
        reportTime {
            LOG.info("Hovering at ${position.textDocument.uri} ${position.position.line}:${position.position.character}")

            val recover = recover(position) ?: return cantRecover(position)
            val (location, decl) = recover.hover() ?: return noHover(position)
            val hoverText = DECL_RENDERER.render(decl)
            val hover = Either.forRight<String, MarkedString>(MarkedString("kotlin", hoverText))
            val range = Range(
                    position(recover.fileContent, location.startOffset),
                    position(recover.fileContent, location.endOffset))

            return CompletableFuture.completedFuture(Hover(listOf(hover), range))
        }
    }

    private fun recover(position: TextDocumentPositionParams): CompiledCode? {
        val file = Paths.get(URI.create(position.textDocument.uri))
        val active = activeDocuments[file] ?: throw RuntimeException("$file is not open")
        val offset = offset(active.content, position.position.line, position.position.character)
        val compiled = workspace.compiledFile(file)
        val recompileStrategy = compiled.recompile(active.content, offset)

        return when (recompileStrategy) {
            Function ->
                compiled.recompileFunction(active.content, offset, workspace.sourcePath())
            File ->
                workspace.recompile(file, active.content).compiledCode(offset, workspace.sourcePath())
            NoChanges ->
                compiled.compiledCode(offset, workspace.sourcePath())
            Impossible ->
                null
        }
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
        reportTime {
            LOG.info("Completing at ${describePosition(position)}")

            val started = System.currentTimeMillis()
            val recover = recover(position) ?: return cantRecover(position)
            val completions = recover.completions()
            val list = completions.map(::completionItem).take(MAX_COMPLETION_ITEMS).toList()
            val isIncomplete = list.size == MAX_COMPLETION_ITEMS

            LOG.info("Found ${list.size} items in ${System.currentTimeMillis() - started} ms")

            return CompletableFuture.completedFuture(Either.forRight(CompletionList(isIncomplete, list)))
        }
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
        val file = Paths.get(URI.create(params.textDocument.uri))
        activeDocuments[file] = ActiveDocument(params.textDocument.text, params.textDocument.version)
        workspace.onOpen(file, params.textDocument.text)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }

    override fun signatureHelp(position: TextDocumentPositionParams): CompletableFuture<SignatureHelp?> {
        reportTime {
            LOG.info("Signature help at ${describePosition(position)}")

            val recover = recover(position) ?: return cantRecover(position)
            val (declarations, activeDeclaration, activeParameter) = recover.signatureHelp() ?: return noFunctionCall(position)
            val signatures = declarations.map(::toSignature)
            val result = SignatureHelp(signatures, activeDeclaration, activeParameter)

            return CompletableFuture.completedFuture(result)
        }
    }

    private fun toSignature(desc: CallableDescriptor): SignatureInformation {
        val label = DECL_RENDERER.render(desc)
        val params = desc.valueParameters.map(::toParameter)
        val docstring = docstring(desc)

        return SignatureInformation(label, docstring, params)
    }

    private fun toParameter(param: ValueParameterDescriptor): ParameterInformation {
        val label = DECL_RENDERER.renderValueParameters(listOf(param), false)
        val removeParens = label.substring(1, label.length - 1)
        val docstring = docstring(param)

        return ParameterInformation(removeParens, docstring)
    }

    private fun docstring(desc: DeclarationDescriptorWithSource): String {
        val doc = findDoc(desc) ?: return ""

        return doc.getContent().trim()
    }

    private fun<T> noFunctionCall(position: TextDocumentPositionParams): CompletableFuture<T?> {
        LOG.info("No function call around ${describePosition(position)}")

        return CompletableFuture.completedFuture(null)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val file = Paths.get(URI.create(params.textDocument.uri))
        activeDocuments.remove(file)
        workspace.onClose(file)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        TODO("not implemented")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val document = params.textDocument
        val file = Paths.get(URI.create(document.uri))
        val existing = activeDocuments[file]!!
        var newText = existing.content

        if (document.version > existing.version) {
            for (change in params.contentChanges) {
                if (change.range == null)
                    activeDocuments[file] = ActiveDocument(change.text, document.version)
                else
                    newText = patch(newText, change)
            }

            activeDocuments[file] = ActiveDocument(newText, document.version)
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

    private data class ActiveDocument(val content: String, val version: Int)
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