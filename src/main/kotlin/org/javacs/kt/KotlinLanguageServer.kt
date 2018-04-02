package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class KotlinLanguageServer: LanguageServer, LanguageClientAware {
    private var client: LanguageClient? = null
    private var textDocuments: KotlinTextDocumentService? = null
    private var workspaces: KotlinWorkspaceService? = null

    override fun connect(client: LanguageClient) {
        this.client = client

        LOG.info("Connected to client")
    }

    override fun shutdown(): CompletableFuture<Any> {
        return completedFuture(null)
    }

    override fun getTextDocumentService(): KotlinTextDocumentService {
        return textDocuments!!
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

        workspaces = KotlinWorkspaceService(initialWorkspaceRoots(params))
        textDocuments = KotlinTextDocumentService(workspaces!!)

        return completedFuture(InitializeResult(capabilities))
    }

    override fun getWorkspaceService(): KotlinWorkspaceService {
        return workspaces!!
    }
}

private fun initialWorkspaceRoots(params: InitializeParams): Set<Path> {
    val result = mutableSetOf<Path>()

    if (params.rootUri != null)
        result.add(Paths.get(URI(params.rootUri)))

    // TODO add workspaceFolders when lsp4j adds it

    return result
}

