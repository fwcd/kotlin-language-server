package org.javacs.kt

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentSyncKind
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

        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun getWorkspaceService(): WorkspaceService {
        return KotlinWorkspaceService()
    }
}

