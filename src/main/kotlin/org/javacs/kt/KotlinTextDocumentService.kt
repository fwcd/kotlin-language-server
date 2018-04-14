package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.javacs.kt.completion.completions
import org.javacs.kt.definition.goToDefinition
import org.javacs.kt.docs.findDoc
import org.javacs.kt.hover.DECL_RENDERER
import org.javacs.kt.hover.RenderCompletionItem
import org.javacs.kt.hover.hoverAt
import org.javacs.kt.position.location
import org.javacs.kt.position.offset
import org.javacs.kt.references.findReferences
import org.javacs.kt.signatureHelp.SignatureHelpSession
import org.javacs.kt.symbols.documentSymbols
import org.javacs.kt.symbols.symbolInformation
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

private const val MAX_COMPLETION_ITEMS = 50

class KotlinTextDocumentService(private val sourceFiles: SourceFiles, private val sourcePath: SourcePath) : TextDocumentService {

    private fun recover(position: TextDocumentPositionParams): CompiledCode {
        val file = Paths.get(URI.create(position.textDocument.uri))
        val content = sourcePath.content(file)
        val offset = offset(content, position.position.line, position.position.character)

        return sourcePath.compiledCode(file, offset)
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
            val list = completions.map(::completionItem).take(MAX_COMPLETION_ITEMS).toList()
            val isIncomplete = list.size == MAX_COMPLETION_ITEMS

            LOG.info("Found ${list.size} items")

            return CompletableFuture.completedFuture(Either.forRight(CompletionList(isIncomplete, list)))
        }
    }

    private fun completionItem(desc: DeclarationDescriptor): CompletionItem =
            desc.accept(RenderCompletionItem(), null)

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        TODO("not implemented")
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<SymbolInformation>> {
        LOG.info("Find symbols in ${params.textDocument}")

        reportTime {
            val path = Paths.get(URI(params.textDocument.uri))
            // TODO does this work without compiling?
            val (file, _) = sourcePath.compiledFile(path)
            val decls = documentSymbols(file)
            val infos = decls.mapNotNull(::symbolInformation).toList()

            return CompletableFuture.completedFuture(infos)
        }
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val file = Paths.get(URI.create(params.textDocument.uri))

        sourceFiles.open(file, params.textDocument.text, params.textDocument.version)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }

    override fun signatureHelp(position: TextDocumentPositionParams): CompletableFuture<SignatureHelp?> {
        reportTime {
            LOG.info("Signature help at ${describePosition(position)}")

            val recover = recover(position)
            val help = SignatureHelpSession(recover)
            val (declarations, activeDeclaration, activeParameter) = help.signatureHelp() ?: return noFunctionCall(position)
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

        sourceFiles.close(file)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        TODO("not implemented")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        sourceFiles.edit(params)
    }

    override fun references(position: ReferenceParams): CompletableFuture<List<Location>> {
        val file = Paths.get(URI.create(position.textDocument.uri))
        val content = sourcePath.content(file)
        val offset = offset(content, position.position.line, position.position.character)
        val found = findReferences(file, offset, sourcePath)
                .map { location(it) }
                .toList()

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