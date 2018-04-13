package org.javacs.kt

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.services.LanguageClient
import org.javacs.kt.RecompileStrategy.*
import org.javacs.kt.RecompileStrategy.Function
import org.javacs.kt.diagnostic.KotlinDiagnostic
import org.javacs.kt.diagnostic.convertDiagnostic
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.BufferedReader
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

private class OpenFile(
        var content: String,
        var version: Int,
        var open: Boolean,
        var parsed: KtFile,
        var parsedVersion: Int,
        var compiled: BindingContext? = null,
        var compiledVersion: Int? = null)

class SourcePath(private val cp: CompilerClassPath) {
    private val workspaceRoots = mutableSetOf<Path>()
    private val files = mutableMapOf<Path, OpenFile>()
    private var client: LanguageClient? = null

    /**
     * Get the latest content of a file
     */
    fun content(file: Path): String {
        return files[file]!!.content
    }

    /**
     * Compile the latest version of a file
     */
    fun compiledFile(file: Path): Pair<KtFile, BindingContext> {
        val compiled = compileIfChanged(file)

        return Pair(compiled.file, compiled.context)
    }

    /**
     * Compile the latest version of the region around `offset`
     */
    fun compiledCode(file: Path, offset: Int): CompiledCode {
        val open = files[file]!!
        val compiled = compileIfNull(file)
        val recompileStrategy = compiled.recompile(open.content, offset)

        return when (recompileStrategy) {
            NoChanges ->
                compiled.compiledCode(offset, all())
            Function ->
                compiled.recompileFunction(open.content, offset, all())
            File, Impossible ->
                compileIfChanged(file).compiledCode(offset, all())
        }
    }

    /**
     * Get parsed trees for all files on source path
     */
    fun all(): Collection<KtFile> =
            files.values.map { it.parsed }

    fun open(file: Path, content: String, version: Int) {
        files[file] = createMemoryFile(content, version, file)

        compileIfNull(file)
    }

    fun close(file: Path) {
        files[file] = openDiskFile(file)
    }

    /**
     * Edit a file, but don't re-compile yet
     */
    fun edit(params: DidChangeTextDocumentParams) {
        val document = params.textDocument
        val file = Paths.get(URI.create(document.uri))
        val existing = files[file]!!
        var newText = existing.content

        if (document.version > existing.version) {
            for (change in params.contentChanges) {
                if (change.range == null) newText = change.text
                else newText = patch(newText, change)
            }

            existing.content = newText
            existing.version = document.version
            lintLater()
        } else LOG.warning("""Ignored change with version ${document.version} <= ${existing.version}""")
    }

    private fun patch(sourceText: String, change: TextDocumentContentChangeEvent): String {
        try {
            val range = change.range
            val reader = BufferedReader(StringReader(sourceText))
            val writer = StringWriter()

            // Skip unchanged lines
            var line = 0

            while (line < range.start.line) {
                writer.write(reader.readLine() + '\n')
                line++
            }

            // Skip unchanged chars
            for (character in 0 until range.start.character) writer.write(reader.read())

            // Write replacement text
            writer.write(change.text)

            // Skip replaced text
            reader.skip(change.rangeLength!!.toLong())

            // Write remaining text
            while (true) {
                val next = reader.read()

                if (next == -1) return writer.toString()
                else writer.write(next)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun connect(client: LanguageClient) {
        this.client = client
    }

    private fun parseIfChanged(file: Path): KtFile {
        val open = files[file]!!

        if (open.version != open.parsedVersion) {
            open.parsed = cp.compiler.createFile(file, open.content)
            open.parsedVersion = open.version
        }

        return open.parsed
    }

    private fun compileIfNull(file: Path): CompiledFile {
        val open = files[file]!!

        if (open.compiled == null) {
            doCompile(file)
        }

        return CompiledFile(file, open.parsed, open.compiled!!, cp)
    }

    private fun compileIfChanged(file: Path): CompiledFile {
        val open = files[file]!!

        parseIfChanged(file)

        if (open.parsedVersion != open.compiledVersion) {
            doCompile(file)
        }

        return CompiledFile(file, open.parsed, open.compiled!!, cp)
    }

    private fun doCompile(file: Path) {
        val open = files[file]!!

        open.compiled = cp.compiler.compileFile(open.parsed, all())
        open.compiledVersion = open.parsedVersion
        reportDiagnostics(file, open.compiled!!.diagnostics.toList())
    }

    private fun createMemoryFile(content: String, version: Int, file: Path): OpenFile {
        val parse = cp.compiler.createFile(file, content)

        return OpenFile(content, version, true, parse, version)
    }

    private fun openDiskFile(file: Path): OpenFile {
        val parse = cp.compiler.openFile(file)

        return OpenFile(parse.text, -1, false, parse, -1)
    }

    var lintCount = 0

    fun reportDiagnostics(compiledFile: Path, kotlinDiagnostics: List<KotlinDiagnostic>) {
        val langServerDiagnostics = kotlinDiagnostics.flatMap(::convertDiagnostic)
        val byFile = langServerDiagnostics.groupBy({ it.first }, { it.second })

        for ((file, diagnostics) in byFile) {
            client!!.publishDiagnostics(PublishDiagnosticsParams(file.toUri().toString(), diagnostics))

            LOG.info("Reported ${diagnostics.size} diagnostics in $file")
        }

        if (!byFile.containsKey(compiledFile)) {
            client!!.publishDiagnostics(PublishDiagnosticsParams(compiledFile.toUri().toString(), listOf()))

            LOG.info("No diagnostics in $compiledFile")
        }

        // LOG.log(Level.WARNING, "LINT", Exception())

        lintCount++
    }

    val debounceLint = Debounce(1.0)

    private fun lintLater() {
        debounceLint.submit(::doLint)
    }

    private fun doLint() {
        for ((file, open) in files) {
            if (open.open)
                compileIfChanged(file)
        }
    }

    fun createdOnDisk(file: Path) {
        changedOnDisk(file)
    }

    fun deletedOnDisk(file: Path) {
        if (isSource(file))
            files.remove(file)
    }

    fun changedOnDisk(file: Path) {
        if (isSource(file))
            files[file] = openDiskFile(file)
    }

    private fun isSource(file: Path): Boolean {
        val name = file.fileName.toString()

        return name.endsWith(".kt") || name.endsWith(".kts")
    }

    fun addWorkspaceRoot(root: Path) {
        val addSources = findSourceFiles(root)

        logAdded(addSources, root)

        for (file in addSources) {
            files[file] = openDiskFile(file)
        }

        workspaceRoots.add(root)
    }

    fun removeWorkspaceRoot(root: Path) {
        val rmSources = files.keys.filter { it.startsWith(root) }

        logRemoved(rmSources, root)

        for (file in rmSources) {
            files.remove(file)
        }

        workspaceRoots.remove(root)
    }

    fun compileFiles(files: Collection<KtFile>): BindingContext =
            cp.compiler.compileFiles(files, all())
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
