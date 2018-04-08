package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class KotlinLanguageServer: LanguageServer, LanguageClientAware {
    private val sourcePath = SourcePath()
    private val workspaces = KotlinWorkspaceService(sourcePath)
    private val textDocuments = KotlinTextDocumentService(sourcePath)

    override fun connect(client: LanguageClient) {
        sourcePath.connect(client)

        LOG.info("Connected to client")
    }

    override fun shutdown(): CompletableFuture<Any> {
        return completedFuture(null)
    }

    override fun getTextDocumentService(): KotlinTextDocumentService {
        return textDocuments
    }

    override fun exit() {
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val capabilities = ServerCapabilities()
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental)
        capabilities.workspace = WorkspaceServerCapabilities()
        capabilities.workspace.workspaceFolders = WorkspaceFoldersOptions()
        capabilities.workspace.workspaceFolders.supported = true
        capabilities.workspace.workspaceFolders.changeNotifications = Either.forRight(true)
        capabilities.hoverProvider = true
        capabilities.completionProvider = CompletionOptions(false, listOf("."))
        capabilities.signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))

        if (params.rootUri != null) {
            LOG.info("Adding workspace ${params.rootUri} to source path")

            sourcePath.addWorkspaceRoot(Paths.get(URI.create(params.rootUri)))
        }

        return completedFuture(InitializeResult(capabilities))
    }

    override fun getWorkspaceService(): KotlinWorkspaceService {
        return workspaces
    }
}