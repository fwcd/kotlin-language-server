package org.javacs.kt

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import org.javacs.kt.diagnostic.KotlinDiagnostic
import org.javacs.kt.diagnostic.convertDiagnostic
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class SourcePath(private val cp: CompilerClassPath) {
    val workspaceRoots = mutableSetOf<Path>()
    val diskFiles = mutableMapOf<Path, KtFile>()
    val openFiles = mutableMapOf<Path, OpenFile>()
    private var client: LanguageClient? = null

    fun connect(client: LanguageClient) {
        this.client = client
    }

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
        val ktFile = cp.compiler.createFile(file, content)
        val sourcePath = diskFiles.values + openFiles.values.map { it.compiled.file } + ktFile
        val context = cp.compiler.compileFile(ktFile, sourcePath)
        val compiled = CompiledFile(file, ktFile, context, cp)

        openFiles[file] = OpenFile(content, version, compiled)

        reportDiagnostics(file, compiled.context.diagnostics.toList())

        return compiled
    }

    fun close(file: Path) {
        openFiles.remove(file)
        diskFiles[file] = cp.compiler.openFile(file)
    }

    fun reportDiagnostics(compiledFile: Path, kotlinDiagnostics: List<KotlinDiagnostic>) {
        // TODO instead of recompiling the whole file, try to recover incrementally
        recompileChangedFiles()

        val langServerDiagnostics = kotlinDiagnostics.flatMap(::convertDiagnostic)
        val byFile = langServerDiagnostics.groupBy({ it.first }, { it.second })

        for ((file, diagnostics) in byFile) {
            client!!.publishDiagnostics(PublishDiagnosticsParams(file.toUri().toString(), diagnostics))

            LOG.info("Reported ${diagnostics.size} diagnostics in $file")
        }

        if (!byFile.containsKey(compiledFile)) {
            client!!.publishDiagnostics(PublishDiagnosticsParams(compiledFile.toUri().toString(), listOf()))

            LOG.info("Cleared diagnostics in $compiledFile")
        }
    }

    fun recompileChangedFiles() {
        for ((file, open) in openFiles) {
            if (open.content != open.compiled.file.text)
                compile(file, open.content, open.version)
        }
    }

    private fun openFileText(file: Path) =
            openFiles[file]?.content

    fun createdOnDisk(file: Path) {
        changedOnDisk(file)
    }

    fun deletedOnDisk(file: Path) {
        if (isSource(file))
            diskFiles.remove(file)
    }

    fun changedOnDisk(file: Path) {
        if (isSource(file))
            diskFiles[file] = cp.compiler.openFile(file)
    }

    private fun isSource(file: Path): Boolean {
        val name = file.fileName.toString()

        return name.endsWith(".kt") || name.endsWith(".kts")
    }

    fun addWorkspaceRoot(root: Path) {
        val addSources = findSourceFiles(root)

        logAdded(addSources, root)

        for (file in addSources) {
            diskFiles[file] = cp.compiler.openFile(file)
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
    val pattern = FileSystems.getDefault().getPathMatcher("glob:*.{kt,kts}")
    return Files.walk(root).filter { pattern.matches(it.fileName) } .collect(Collectors.toSet())
}

private fun logAdded(sources: Collection<Path>, rootPath: Path?) {
    if (sources.size > 5) LOG.info("Adding ${sources.size} files under $rootPath to source path")
    else LOG.info("Adding ${sources.joinToString(", ")} to source path")
}

private fun logRemoved(sources: Collection<Path>, rootPath: Path?) {
    if (sources.size > 5) LOG.info("Removing ${sources.size} files under $rootPath to source path")
    else LOG.info("Removing ${sources.joinToString(", ")} to source path")
}

data class OpenFile(val content: String, val version: Int, val compiled: CompiledFile)