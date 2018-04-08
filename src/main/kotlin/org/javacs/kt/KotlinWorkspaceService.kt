package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class KotlinWorkspaceService(private val sourcePath: SourcePath) : WorkspaceService {

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        for (change in params.changes) {
            val path = Paths.get(URI.create(change.uri))

            when (change.type) {
                FileChangeType.Created -> {
                    sourcePath.createdOnDisk(path)
                }
                FileChangeType.Deleted -> {
                    sourcePath.deletedOnDisk(path)
                }
                FileChangeType.Changed -> {
                    sourcePath.changedOnDisk(path)
                }
                null -> {
                    // Nothing to do
                }
            }
        }
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        LOG.info(params.toString())
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<MutableList<out SymbolInformation>> {
        TODO("not implemented")
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        for (change in params.event.added) {
            LOG.info("Adding workspace ${change.uri} to source path")

            sourcePath.addWorkspaceRoot(Paths.get(URI.create(change.uri)))
        }
        for (change in params.event.removed) {
            LOG.info("Dropping workspace ${change.uri} from source path")

            sourcePath.removeWorkspaceRoot(Paths.get(URI.create(change.uri)))
        }
    }
}