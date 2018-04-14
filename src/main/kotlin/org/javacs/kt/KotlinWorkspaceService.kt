package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService
import org.javacs.kt.completion.containsCharactersInOrder
import org.javacs.kt.symbols.symbolInformation
import org.javacs.kt.symbols.workspaceSymbols
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class KotlinWorkspaceService(private val sourceFiles: SourceFiles, private val sourcePath: SourcePath, private val classPath: CompilerClassPath) : WorkspaceService {

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        for (change in params.changes) {
            val path = Paths.get(URI.create(change.uri))

            when (change.type) {
                FileChangeType.Created -> {
                    sourceFiles.createdOnDisk(path)
                    classPath.createdOnDisk(path)
                }
                FileChangeType.Deleted -> {
                    sourceFiles.deletedOnDisk(path)
                    classPath.deletedOnDisk(path)
                }
                FileChangeType.Changed -> {
                    sourceFiles.changedOnDisk(path)
                    classPath.changedOnDisk(path)
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

    private val maxSymbols = 50

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<List<SymbolInformation>> {
        val result = workspaceSymbols(sourcePath)
                .filter { containsCharactersInOrder(it.name!!, params.query, false) }
                .mapNotNull(::symbolInformation)
                .take(maxSymbols)
                .toList()

        return CompletableFuture.completedFuture(result)
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        for (change in params.event.added) {
            LOG.info("Adding workspace ${change.uri} to source path")

            val root = Paths.get(URI.create(change.uri))

            sourceFiles.addWorkspaceRoot(root)
            classPath.addWorkspaceRoot(root)
        }
        for (change in params.event.removed) {
            LOG.info("Dropping workspace ${change.uri} from source path")

            val root = Paths.get(URI.create(change.uri))

            sourceFiles.removeWorkspaceRoot(root)
            classPath.removeWorkspaceRoot(root)
        }
    }
}