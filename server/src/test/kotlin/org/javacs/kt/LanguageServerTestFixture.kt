package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.junit.Before
import org.junit.After
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

abstract class LanguageServerTestFixture(
    relativeWorkspaceRoot: String,
    config: Configuration = Configuration()
) : LanguageClient {
    val workspaceRoot = absoluteWorkspaceRoot(relativeWorkspaceRoot)
    val languageServer = createLanguageServer(config)

    var diagnostics = listOf<Diagnostic>()
    val errors: List<Diagnostic>
        get() = diagnostics.filter { it.severity == DiagnosticSeverity.Error }
    val warnings: List<Diagnostic>
        get() = diagnostics.filter { it.severity == DiagnosticSeverity.Warning }

    fun absoluteWorkspaceRoot(relativeWorkspaceRoot: String): Path {
        val testResources = testResourcesRoot()
        return testResources.resolve(relativeWorkspaceRoot)
    }

    private fun createLanguageServer(config: Configuration): Server {
        val languageServer = Server(config)
        val init = InitializeParams().apply {
            capabilities = ClientCapabilities().apply {
                textDocument = TextDocumentClientCapabilities().apply {
                    completion = CompletionCapabilities().apply {
                        completionItem = CompletionItemCapabilities().apply {
                            snippetSupport = true
                        }
                    }
                }
            }
        }

        init.workspaceFolders = listOf(WorkspaceFolder().apply {
            name = workspaceRoot.fileName.toString()
            uri = workspaceRoot.toUri().toString()
        })

        languageServer.config.inlayHints.apply {
            typeHints = true
            parameterHints = true
            chainedHints = true
        }
        languageServer.sourcePath.indexEnabled = false
        languageServer.connect(this)
        languageServer.initialize(init).join()

        return languageServer
    }

    @After fun closeLanguageServer() {
        languageServer.close()
    }

    @After fun printMemoryUsage() {
        val rt = Runtime.getRuntime()
        val total = rt.totalMemory().toDouble() / 1000000.0
        val free = rt.freeMemory().toDouble() / 1000000.0
        println("Memory after test: ${total - free} MB used / $total MB total")
    }

    fun renameParams(relativePath: String, line: Int, column: Int, newName: String): RenameParams =
        textDocumentPosition(relativePath, line, column).run { RenameParams(textDocument, position, newName) }

    fun completionParams(relativePath: String, line: Int, column: Int): CompletionParams {
        val file = workspaceRoot.resolve(relativePath)
        val fileId = TextDocumentIdentifier(file.toUri().toString())
        val position = position(line, column)

        return CompletionParams(fileId, position)
    }

    private fun textDocumentPosition(relativePath: String, line: Int, column: Int): TextDocumentPositionParams =
        textDocumentPosition(relativePath, position(line, column))

    fun codeActionParams(relativePath: String, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, diagnostics: List<Diagnostic>, only: List<String>): CodeActionParams {
        val file = workspaceRoot.resolve(relativePath)
        val fileId = TextDocumentIdentifier(file.toUri().toString())
        val range = range(startLine, startColumn, endLine, endColumn)
        val context = CodeActionContext(diagnostics, only)

        return CodeActionParams(fileId, range, context)
    }

    fun hoverParams(relativePath: String, line: Int, column: Int): HoverParams =
        textDocumentPosition(relativePath, line, column).run { HoverParams(textDocument, position) }

    fun inlayHintParams(relativePath: String, range: Range): InlayHintParams =
        textDocumentPosition(relativePath, 0, 0).run { InlayHintParams(textDocument, range) }

    fun semanticTokensParams(relativePath: String): SemanticTokensParams =
        textDocumentPosition(relativePath, 0, 0).run { SemanticTokensParams(textDocument) }

    fun semanticTokensRangeParams(relativePath: String, range: Range): SemanticTokensRangeParams =
        textDocumentPosition(relativePath, 0, 0).run { SemanticTokensRangeParams(textDocument, range) }

    fun signatureHelpParams(relativePath: String, line: Int, column: Int): SignatureHelpParams =
        textDocumentPosition(relativePath, line, column).run { SignatureHelpParams(textDocument, position) }

    fun definitionParams(relativePath: String, line: Int, column: Int): DefinitionParams =
        textDocumentPosition(relativePath, line, column).run { DefinitionParams(textDocument, position) }

    fun definitionParams(relativePath: String, position: Position): DefinitionParams =
        textDocumentPosition(relativePath, position).run { DefinitionParams(textDocument, position) }

    private fun textDocumentPosition(relativePath: String, position: Position): TextDocumentPositionParams {
        val file = workspaceRoot.resolve(relativePath)
        val fileId = TextDocumentIdentifier(file.toUri().toString())
        return TextDocumentPositionParams(fileId, position)
    }

    fun position(line: Int, column: Int) = Position(line - 1, column - 1)

    fun range(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) =
        Range(position(startLine, startColumn), position(endLine, endColumn))

    fun uri(relativePath: String): URI =
            workspaceRoot.resolve(relativePath).toUri()

    fun referenceParams(relativePath: String, line: Int, column: Int): ReferenceParams =
        ReferenceParams(
            TextDocumentIdentifier(uri(relativePath).toString()),
            position(line, column),
            ReferenceContext(true)
        )

    fun open(relativePath: String) {
        val file =  workspaceRoot.resolve(relativePath)
        val content = file.toFile().readText()
        val document = TextDocumentItem(file.toUri().toString(), "Kotlin", 0, content)

        languageServer.textDocumentService.didOpen(DidOpenTextDocumentParams(document))
    }

    private var version = 1

    fun replace(relativePath: String, line: Int, char: Int, oldText: String, newText: String) {
        val range = Range(position(line, char), position(line, char + oldText.length))
        val edit = TextDocumentContentChangeEvent(range, newText)
        val doc = VersionedTextDocumentIdentifier(uri(relativePath).toString(), version++)

        languageServer.textDocumentService.didChange(DidChangeTextDocumentParams(doc, listOf(edit)))
    }

    // LanguageClient functions

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        this.diagnostics = diagnostics.diagnostics
    }

    override fun showMessageRequest(request: ShowMessageRequestParams?): CompletableFuture<MessageActionItem>? {
        println(request.toString())
        return null
    }

    override fun telemetryEvent(`object`: Any?) {
        println(`object`.toString())
    }

    override fun logMessage(message: MessageParams?) = printMessage(message)

    override fun showMessage(message: MessageParams?) = printMessage(message)

    private fun printMessage(message: MessageParams?) {
        println("[${message?.type}] ${message?.message}")
    }
}

fun testResourcesRoot(): Path {
    val anchorTxt = LanguageServerTestFixture::class.java.getResource("/Anchor.txt")?.toURI()
    return anchorTxt?.let { Paths.get(it).parent } ?: throw RuntimeException("Could not find test resources root")
}

open class SingleFileTestFixture(
    relativeWorkspaceRoot: String,
    val file: String,
    config: Configuration = Configuration()
) : LanguageServerTestFixture(relativeWorkspaceRoot, config) {
    @Before fun openFile() {
        open(file)

        // Wait for lint, so subsequent replace(...) operations cause recovery
        languageServer.textDocumentService.lintDebouncer.waitForPendingTask()
    }
}
