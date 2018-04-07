package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.keysToMap
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.stream.Collectors.toSet

class KotlinWorkspaceService(workspaceRoots: Collection<Path>) : WorkspaceService {
    private val diskFiles: MutableMap<Path, KtFile> = workspaceRoots
            .flatMap(::findSourceFiles)
            .keysToMap(Compiler::openFile)
            .toMutableMap()
    private val openFiles = mutableMapOf<Path, CompiledFile>()

    fun sourcePath(): Collection<KtFile> {
        val result = mutableMapOf<Path, KtFile>()

        result.putAll(diskFiles)
        openFiles.forEach {
            path, document -> result[path] = document.file
        }

        return result.values
    }

    fun onOpen(file: Path, content: String) {
        diskFiles.remove(file)
        val ktFile = Compiler.createFile(file, content)
        val context = Compiler.compileFile(ktFile, sourcePath() + ktFile)
        openFiles[file] = CompiledFile(file, ktFile, context)
    }

    fun onClose(file: Path) {
        diskFiles[file] = Compiler.openFile(file)
        openFiles.remove(file)
    }

    fun compiledFile(file: Path): CompiledFile {
        return openFiles[file] ?: throw RuntimeException("$file is not open")
    }

    fun recompile(file: Path, content: String): CompiledFile {
        val existing = openFiles[file] ?: throw RuntimeException("$file is not open")
        val new = existing.recompileFile(content, sourcePath())

        openFiles[file] = new

        return new
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        val newSources = changeWatchedFiles(diskFiles.keys, params)

        updateCompilerIfNeeded(newSources)
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        LOG.info(params.toString())
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<MutableList<out SymbolInformation>> {
        TODO("not implemented")
    }

    fun initialize(params: InitializeParams) {
        if (params.rootUri != null) {
            val root = Paths.get(URI.create(params.rootUri))
            val sources = findSourceFiles(root)

            logAdded(sources, root)

            updateCompilerIfNeeded(sources)
        }
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        val newSources = changeWorkspaceRoots(diskFiles.keys, params)

        updateCompilerIfNeeded(newSources)
    }

    private fun updateCompilerIfNeeded(newSources: Set<Path>) {
        if (newSources != diskFiles.keys) {
            val added = newSources - diskFiles.keys
            val removed = diskFiles.keys - newSources

            for (each in removed) {
                diskFiles.remove(each)
                openFiles.remove(each)
            }

            for (each in added) {
                diskFiles[each] = Compiler.openFile(each)
            }
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

private fun findSourceFiles(root: Path): Set<Path> {
    val pattern = FileSystems.getDefault().getPathMatcher("glob:*.kt")
    return Files.walk(root).filter { pattern.matches(it.fileName) } .collect(toSet())
}

private fun logAdded(sources: Collection<Path>, rootPath: Path?) {
    if (sources.size > 5) LOG.info("Adding ${sources.size} files under $rootPath")
    else LOG.info("Adding ${sources.joinToString(", ")}")
}

private fun logRemoved(sources: Collection<Path>, rootPath: Path?) {
    if (sources.size > 5) LOG.info("Removing ${sources.size} files under $rootPath")
    else LOG.info("Removing ${sources.joinToString(", ")}")
}