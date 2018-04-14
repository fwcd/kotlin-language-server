package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService
import org.javacs.kt.symbols.workspaceSymbols
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class KotlinWorkspaceService(private val sf: SourceFiles, private val sp: SourcePath, private val cp: CompilerClassPath) : WorkspaceService {

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        for (change in params.changes) {
            val path = Paths.get(URI.create(change.uri))

            when (change.type) {
                FileChangeType.Created -> {
                    sf.createdOnDisk(path)
                    cp.createdOnDisk(path)
                }
                FileChangeType.Deleted -> {
                    sf.deletedOnDisk(path)
                    cp.deletedOnDisk(path)
                }
                FileChangeType.Changed -> {
                    sf.changedOnDisk(path)
                    cp.changedOnDisk(path)
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

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<List<SymbolInformation>> {
        val result = workspaceSymbols(params.query, sp)

        return CompletableFuture.completedFuture(result)
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        for (change in params.event.added) {
            LOG.info("Adding workspace ${change.uri} to source path")

            val root = Paths.get(URI.create(change.uri))

            sf.addWorkspaceRoot(root)
            cp.addWorkspaceRoot(root)
        }
        for (change in params.event.removed) {
            LOG.info("Dropping workspace ${change.uri} from source path")

            val root = Paths.get(URI.create(change.uri))

            sf.removeWorkspaceRoot(root)
            cp.removeWorkspaceRoot(root)
        }
    }
}