package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors.toList

class KotlinWorkspaceService(workspaceRoots: Collection<Path>) : WorkspaceService {
    private var sources = workspaceRoots.flatMap(::findSourceFiles).toSet()
    private var compiler = Compiler.fromPaths(sources)
    private var activeDocuments = mutableMapOf<Path, LiveFile>()

    fun onOpen(file: Path, content: String) {
        updateCompilerIfNeeded(sources.plusElement(file))
        compiler.openForEditing(file, content)
        activeDocuments[file] = LiveFile(compiler, file, content)
    }

    fun onClose(file: Path) {
        activeDocuments.remove(file)
        compiler.close(file)
    }

    fun liveFile(file: Path): LiveFile {
        return activeDocuments[file] ?: throw RuntimeException("$file is not open")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        val newSources = changeWatchedFiles(sources, params)

        updateCompilerIfNeeded(newSources)
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        LOG.info(params.toString())
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<MutableList<out SymbolInformation>> {
        TODO("not implemented")
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        val newSources = changeWorkspaceRoots(sources, params)

        updateCompilerIfNeeded(newSources)
    }

    private fun updateCompilerIfNeeded(newSources: Set<Path>) {
        if (newSources != sources) {
            sources = newSources
            compiler = Compiler.fromPaths(newSources)
            activeDocuments = activeDocuments
                    .filterKeys { newSources.contains(it) }
                    .mapValues { LiveFile(compiler, it.key, it.value.text) }
                    .toMutableMap()
        }
    }
}

private fun changeWatchedFiles(originalSources: Set<Path>, params: DidChangeWatchedFilesParams): Set<Path> {
    var sources = originalSources

    for (change in params.changes) {
        when (change.type!!) {
            FileChangeType.Created -> {
                LOG.info("Adding source $change.uri to compiler")

                sources += Paths.get(URI.create(change.uri))
            }
            FileChangeType.Deleted -> {
                LOG.info("Dropping source $change.uri from compiler")

                sources -= Paths.get(URI.create(change.uri))
            }
            FileChangeType.Changed -> {
                // nothing to do
            }
        }
    }

    return sources
}

private fun changeWorkspaceRoots(originalSources: Set<Path>, params: DidChangeWorkspaceFoldersParams): Set<Path> {
    var sources = originalSources

    for (root in params.event.added) {
        val rootPath = Paths.get(URI.create(root.uri))
        val removed = sources.filter { it.startsWith(rootPath) }

        if (!removed.isEmpty()) {
            logRemoved(removed, rootPath)
            sources -= removed
        }
    }

    for (root in params.event.removed) {
        val rootPath = Paths.get(URI.create(root.uri))
        val added = sources.filter { it.startsWith(rootPath) }

        if (!added.isEmpty()) {
            logAdded(added, rootPath)
            sources += added
        }
    }

    return sources
}

private fun findSourceFiles(root: Path): Collection<Path> {
    val pattern = FileSystems.getDefault().getPathMatcher("glob:*.kt")
    return Files.walk(root).filter { pattern.matches(it.fileName) } .collect(toList())
}

private fun logAdded(removed: List<Path>, rootPath: Path?) {
    if (removed.size > 5) LOG.info("Removing ${removed.size} files under $rootPath")
    else LOG.info("Removing ${removed.joinToString(", ")}")
}

private fun logRemoved(removed: List<Path>, rootPath: Path?) {
    if (removed.size > 5) LOG.info("Removing ${removed.size} files under $rootPath")
    else LOG.info("Removing ${removed.joinToString(", ")}")
}