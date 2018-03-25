package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class KotlinWorkspaceService : WorkspaceService {
    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        LOG.info(params.toString())
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        LOG.info(params.toString())
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<MutableList<out SymbolInformation>> {
        TODO("not implemented")
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams?) {
        // TODO
        LOG.info(params.toString())
    }
}