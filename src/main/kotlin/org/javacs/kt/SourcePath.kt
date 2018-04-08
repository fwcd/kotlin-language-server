package org.javacs.kt

import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class SourcePath {
    val workspaceRoots = mutableSetOf<Path>()
    val diskFiles = mutableMapOf<Path, KtFile>()
    val openFiles = mutableMapOf<Path, OpenFile>()

    fun open(file: Path, content: String, version: Int): CompiledFile {
        return compile(file, content, version)
    }

    fun editOpenFile(file: Path, content: String, version: Int) {
        assert(version > openFiles[file]!!.version)

        compile(file, content, version)
    }

    fun recompileOpenFile(file: Path): CompiledFile {
        val open = openFiles[file]!!

        return compile(file, open.content, open.version)
    }

    private fun compile(file: Path, content: String, version: Int): CompiledFile {
        LOG.info("Compile $file")

        // Remove the old file immediately so it doesn't show up in the source path
        diskFiles.remove(file)
        openFiles.remove(file)

        // Compile the new content
        val ktFile = Compiler.createFile(file, content)
        val sourcePath = diskFiles.values + openFiles.values.map { it.compiled.file } + ktFile
        val context = Compiler.compileFile(ktFile, sourcePath)
        val compiled = CompiledFile(file, ktFile, context)

        openFiles[file] = OpenFile(content, version, compiled)

        return compiled
    }

    fun close(file: Path) {
        openFiles.remove(file)
        diskFiles[file] = Compiler.openFile(file)
    }

    fun recompileChangedFiles(): List<CompiledFile> =
            openFiles.keys.mapNotNull(::recompileIfChanged)

    private fun recompileIfChanged(file: Path): CompiledFile? {
        val open = openFiles[file] ?: return null

        if (open.content != open.compiled.file.text)
            return compile(file, open.content, open.version)
        else
            return null
    }

    fun createdOnDisk(file: Path) {
        diskFiles[file] = Compiler.openFile(file)
    }

    fun deletedOnDisk(file: Path) {

    }

    fun changedOnDisk(file: Path) {

    }

    fun addWorkspaceRoot(root: Path) {
        val addSources = findSourceFiles(root)

        logAdded(addSources, root)

        for (file in addSources) {
            diskFiles[file] = Compiler.openFile(file)
        }

        workspaceRoots.add(root)
    }

    fun removeWorkspaceRoot(root: Path) {
        val rmSources = diskFiles.keys.filter { it.startsWith(root) }

        logRemoved(rmSources, root)

        for (file in rmSources) {
            diskFiles.remove(file)
        }

        workspaceRoots.remove(root)
    }

    fun allSources(): Collection<KtFile> {
        val open = openFiles.values.map { it.compiled.file }
        val disk = diskFiles.values

        return open + disk
    }
}

private fun findSourceFiles(root: Path): Set<Path> {
    val pattern = FileSystems.getDefault().getPathMatcher("glob:*.kt")
    return Files.walk(root).filter { pattern.matches(it.fileName) } .collect(Collectors.toSet())
}

private fun logAdded(sources: Collection<Path>, rootPath: Path?) {
    if (sources.size > 5) LOG.info("Adding ${sources.size} files under $rootPath")
    else LOG.info("Adding ${sources.joinToString(", ")}")
}

private fun logRemoved(sources: Collection<Path>, rootPath: Path?) {
    if (sources.size > 5) LOG.info("Removing ${sources.size} files under $rootPath")
    else LOG.info("Removing ${sources.joinToString(", ")}")
}

data class OpenFile(val content: String, val version: Int, val compiled: CompiledFile)