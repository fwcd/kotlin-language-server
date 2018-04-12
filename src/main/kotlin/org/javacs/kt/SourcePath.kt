package org.javacs.kt

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import org.javacs.kt.RecompileStrategy.*
import org.javacs.kt.RecompileStrategy.Function
import org.javacs.kt.diagnostic.KotlinDiagnostic
import org.javacs.kt.diagnostic.convertDiagnostic
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class OpenFile(val content: String, val version: Int, val compiled: CompiledFile)

class SourcePath(private val cp: CompilerClassPath) {
    private val workspaceRoots = mutableSetOf<Path>()
    private val diskFiles = mutableMapOf<Path, KtFile>()
    private val openFiles = mutableMapOf<Path, OpenFile>()
    private var client: LanguageClient? = null

    fun openFile(file: Path): OpenFile? =
            openFiles[file]

    fun connect(client: LanguageClient) {
        this.client = client
    }

    fun open(file: Path, content: String, version: Int): CompiledFile {
        return compileOpenFile(file, content, version)
    }

    fun editOpenFile(file: Path, content: String, version: Int) {
        assert(version > openFiles[file]!!.version)

        val open = openFiles[file] ?: throw RuntimeException("$file is not open")
        val edit = OpenFile(content, version, open.compiled)

        openFiles[file] = edit
    }

    fun recompileOpenFile(file: Path): CompiledFile {
        val open = openFiles[file]!!

        return compileOpenFile(file, open.content, open.version)
    }

    private fun compileOpenFile(file: Path, content: String, version: Int): CompiledFile {
        LOG.info("Compile $file")

        // Compile the new content
        val ktFile = cp.compiler.createFile(file, content)
        val sourcePath = allSources() - file + Pair(file, ktFile)
        val context = cp.compiler.compileFile(ktFile, sourcePath.values)
        val compiled = CompiledFile(file, ktFile, context, cp)

        openFiles[file] = OpenFile(content, version, compiled)

        reportDiagnostics(file, compiled.context.diagnostics.toList())

        return compiled
    }

    private fun openDiskFile(file: Path): KtFile =
            cp.compiler.openFile(file)

    fun close(file: Path) {
        openFiles.remove(file)
        diskFiles[file] = openDiskFile(file)
    }

    var lintCount = 0

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

        // LOG.log(Level.WARNING, "LINT", Exception())

        lintCount++
    }

    fun recompileChangedFiles() {
        for ((file, open) in openFiles) {
            if (open.content != open.compiled.file.text)
                compileOpenFile(file, open.content, open.version)
        }
    }

    fun createdOnDisk(file: Path) {
        changedOnDisk(file)
    }

    fun deletedOnDisk(file: Path) {
        if (isSource(file))
            diskFiles.remove(file)
    }

    fun changedOnDisk(file: Path) {
        if (isSource(file))
            diskFiles[file] = openDiskFile(file)
    }

    private fun isSource(file: Path): Boolean {
        val name = file.fileName.toString()

        return name.endsWith(".kt") || name.endsWith(".kts")
    }

    fun addWorkspaceRoot(root: Path) {
        val addSources = findSourceFiles(root)

        logAdded(addSources, root)

        for (file in addSources) {
            diskFiles[file] = openDiskFile(file)
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

    fun allSources(): Map<Path, KtFile> =
            diskFiles + openFiles.mapValues { it.value.compiled.file }

    fun compileFiles(files: Collection<KtFile>): BindingContext =
            cp.compiler.compileFiles(files, allSources().values)

    fun recover(file: Path, offset: Int): CompiledCode? {
        val open = openFile(file) ?: throw RuntimeException("$file is not open")
        val recompileStrategy = open.compiled.recompile(open.content, offset)

        return when (recompileStrategy) {
            Function ->
                open.compiled.recompileFunction(open.content, offset, allSources().values)
            File ->
                recompileOpenFile(file).compiledCode(offset, allSources().values)
            NoChanges ->
                open.compiled.compiledCode(offset, allSources().values)
            Impossible ->
                null
        }
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
