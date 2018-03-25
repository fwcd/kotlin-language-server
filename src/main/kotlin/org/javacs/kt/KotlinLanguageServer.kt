package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class KotlinLanguageServer: LanguageServer, LanguageClientAware {
    private var client: LanguageClient? = null

    override fun connect(client: LanguageClient) {
        this.client = client

        LOG.info("Connected to client")
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(null)
    }

    override fun getTextDocumentService(): TextDocumentService {
        return KotlinTextDocumentService()
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

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun getWorkspaceService(): WorkspaceService {
        return KotlinWorkspaceService()
    }
}

