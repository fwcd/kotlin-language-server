package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

abstract class LanguageServerTestFixture(private val relativeWorkspaceRoot: String): LanguageClient {
    val workspaceRoot = absoluteWorkspaceRoot(relativeWorkspaceRoot)
    val languageServer = createLanguageServer()

    private fun absoluteWorkspaceRoot(relativeWorkspaceRoot: String): Path {
        val testResources = testResourcesRoot()
        return testResources.resolve(relativeWorkspaceRoot)
    }

    private fun createLanguageServer(): KotlinLanguageServer {
        val languageServer = KotlinLanguageServer()
        val init = InitializeParams()

        init.rootUri = workspaceRoot.toUri().toString()
        languageServer.initialize(init)
        languageServer.connect(this)

        return languageServer
    }

    fun position(relativePath: String, line: Int, column: Int): TextDocumentPositionParams {
        val file = workspaceRoot.resolve(relativePath)
        val fileId = TextDocumentIdentifier(file.toUri().toString())
        val position = Position(line - 1, column - 1)

        return TextDocumentPositionParams(fileId, position)
    }

    fun uri(relativePath: String) =
            workspaceRoot.resolve(relativePath).toUri()

    fun open(relativePath: String) {
        val file =  workspaceRoot.resolve(relativePath)
        val content = file.toFile().readText()
        val document = TextDocumentItem(file.toUri().toString(), "Kotlin", 0, content)

        languageServer.textDocumentService.didOpen(DidOpenTextDocumentParams(document))
    }

    fun replace(relativePath: String, line: Int, char: Int, oldText: String, newText: String) {
        val range = Range(Position(line - 1, char - 1), Position(line - 1, char -1 + oldText.length))
        val edit = TextDocumentContentChangeEvent(range, oldText.length, newText)
        val doc = VersionedTextDocumentIdentifier(1)
        doc.uri = uri(relativePath).toString()

        languageServer.textDocumentService.didChange(DidChangeTextDocumentParams(doc, listOf(edit)))
    }

    // LanguageClient functions

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        LOG.info(diagnostics.toString())
    }

    override fun showMessageRequest(request: ShowMessageRequestParams?): CompletableFuture<MessageActionItem>? {
        LOG.info(request.toString())

        return null
    }

    override fun telemetryEvent(`object`: Any?) {
        LOG.info(`object`.toString())
    }

    override fun logMessage(message: MessageParams?) {
        LOG.info(message.toString())
    }

    override fun showMessage(message: MessageParams?) {
        LOG.info(message.toString())
    }
}

fun testResourcesRoot(): Path {
    val anchorTxt = LanguageServerTestFixture::class.java.getResource("/Anchor.txt").toURI()
    return Paths.get(anchorTxt).parent!!
}