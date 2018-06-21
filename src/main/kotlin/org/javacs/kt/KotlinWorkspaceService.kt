package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService
import org.javacs.kt.symbols.workspaceSymbols
import org.javacs.kt.commands.JAVA_TO_KOTLIN_COMMAND
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import com.google.gson.JsonElement
import com.google.gson.Gson

class KotlinWorkspaceService(private val sf: SourceFiles, private val sp: SourcePath, private val cp: CompilerClassPath) : WorkspaceService {
    private val gson = Gson()

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        val args = params.arguments
        LOG.info("Executing command: ${params.command} with ${params.arguments}")

        when (params.command) {
            JAVA_TO_KOTLIN_COMMAND -> {
                val filePath = Paths.get(URI.create(gson.fromJson(args[0] as JsonElement, String::class.java)))
                val range = gson.fromJson(args[1] as JsonElement, Range::class.java)

                // TODO: Do Java to Kotlin conversion
            }
        }

        return CompletableFuture.completedFuture(null)
    }

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
