package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.javacs.kt.RecompileStrategy.*
import org.javacs.kt.RecompileStrategy.Function
import org.javacs.kt.diagnostic.ConvertDiagnostics
import org.javacs.kt.docs.findDoc
import org.javacs.kt.position.offset
import org.javacs.kt.position.position
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

private const val MAX_COMPLETION_ITEMS = 50

class KotlinTextDocumentService(private val sourcePath: SourcePath) : TextDocumentService {
    private var client: LanguageClient? = null

    fun connect(client: LanguageClient) {
        this.client = client
    }

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
        val open = sourcePath.openFiles[file] ?: throw RuntimeException("$file is not open")
        val offset = offset(open.content, position.position.line, position.position.character)
        val recompileStrategy = open.compiled.recompile(open.content, offset)

        return when (recompileStrategy) {
            Function ->
                open.compiled.recompileFunction(open.content, offset, sourcePath.allSources())
            File ->
                sourcePath.recompileOpenFile(file).compiledCode(offset, sourcePath.allSources())
            NoChanges ->
                open.compiled.compiledCode(offset, sourcePath.allSources())
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
        val open = sourcePath.open(file, params.textDocument.text, params.textDocument.version)

        reportDiagnostics(open.context.diagnostics.toList())
    }

    val debounceLint = Debounce(1.0)

    private fun lintLater() {
        debounceLint.submit(::doLint)
    }

    private fun doLint() {
        val changed = sourcePath.recompileChangedFiles()
        val kotlinDiagnostics = changed.flatMap { it.context.diagnostics }

        reportDiagnostics(kotlinDiagnostics)
    }

    private fun reportDiagnostics(kotlinDiagnostics: List<Diagnostic>) {
        val converter = ConvertDiagnostics(::openFileText, ::compiledFileText)
        val langServerDiagnostics = kotlinDiagnostics.flatMap { converter.convert(it) }
        val byFile = langServerDiagnostics.groupBy({ it.first }, { it.second })

        for ((file, diagnostics) in byFile) {
            client!!.publishDiagnostics(PublishDiagnosticsParams(file.toUri().toString(), diagnostics))

            LOG.info("Reported ${diagnostics.size} diagnostics in $file")
        }
    }

    private fun openFileText(file: Path) =
            sourcePath.openFiles[file]?.content

    private fun compiledFileText(file: Path) =
            sourcePath.openFiles[file]?.compiled?.file?.text

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

        sourcePath.close(file)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
        TODO("not implemented")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val document = params.textDocument
        val file = Paths.get(URI.create(document.uri))
        val existing = sourcePath.openFiles[file]!!
        var newText = existing.content

        if (document.version > existing.version) {
            for (change in params.contentChanges) {
                if (change.range == null)
                    newText = change.text
                else
                    newText = patch(newText, change)
            }

            sourcePath.editOpenFile(file, newText, document.version)
            lintLater()
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

private inline fun<T> reportTime(block: () -> T): T {
    val started = System.currentTimeMillis()
    try {
        return block()
    } finally {
        val finished = System.currentTimeMillis()
        LOG.info("Finished in ${finished - started} ms")
    }
}