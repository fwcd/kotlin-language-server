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

class SourcePath(private val cp: CompilerClassPath) {
    private var workspaceRoots = setOf<Path>()
    private val files = SourceFiles()
    private var client: LanguageClient? = null

    /**
     * Prevents concurrent threads from reversing edits to open files
     */
    private class SourceFiles {
        private val files = mutableMapOf<Path, OpenFile>()

        operator fun get(file: Path): OpenFile = files[file]!!

        operator fun plusAssign(new: OpenFile) {
            synchronized(files) {
                val existing = files[new.file]

                if (existing == null || new.version == -1 || existing.version <= new.version)
                    files[new.file] = new
                else
                    LOG.warning("Ignoring ${new.file} version ${new.version} < ${existing.version}")
            }
        }

        operator fun minusAssign(file: Path) {
            synchronized(files) {
                files.remove(file)
            }
        }

        fun removeAll(closed: Collection<Path>) {
            synchronized(files) {
                files -= closed
            }
        }

        fun keys() = files.keys

        fun values() = files.values
    }

    private inner class OpenFile(
            val file: Path,
            val content: String,
            val version: Int,
            val open: Boolean,
            val parsed: KtFile? = null,
            val parsedVersion: Int? = null,
            val compiledFile: KtFile? = null,
            val compiledContext: BindingContext? = null,
            val compiledVersion: Int? = null) {

        fun edit(newContent: String, newVersion: Int): OpenFile {
            val result = OpenFile(file, newContent, newVersion, open, parsed, parsedVersion, compiledFile, compiledContext, compiledVersion)

            files += result

            return result
        }

        fun parseIfChanged(): OpenFile {
            if (version != parsedVersion) {
                val reparse = cp.compiler.createFile(file, content)
                val result = OpenFile(file, content, version, open, reparse, version, compiledFile, compiledContext, compiledVersion)

                files += result

                return result
            }
            else return this
        }

        fun compileIfNull(): OpenFile =
                parseIfChanged().doCompileIfNull()

        private fun doCompileIfNull(): OpenFile =
            if (compiledVersion == null)
                doCompileIfChanged()
            else
                this

        fun compileIfChanged(): OpenFile =
                parseIfChanged().doCompileIfChanged()

        private fun doCompileIfChanged(): OpenFile {
            if (parsedVersion != compiledVersion) {
                val recompile = cp.compiler.compileFile(parsed!!, all())
                val result = OpenFile(file, content, version, open, parsed, parsedVersion, parsed, recompile, parsedVersion)

                files += result
                reportDiagnostics(file, recompile.diagnostics.toList())

                return result
            }
            else return this
        }

        fun prepareCompiledFile(): CompiledFile =
                parseIfChanged().compileIfNull().doPrepareCompiledFile()

        private fun doPrepareCompiledFile(): CompiledFile =
                CompiledFile(content, compiledFile!!, compiledContext!!, all(), cp)
    }

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
        val compiled = files[file]!!.compileIfChanged()

        return Pair(compiled.compiledFile!!, compiled.compiledContext!!)
    }

    /**
     * Compile the latest version of the region around `offset`
     */
    fun compiledCode(file: Path, offset: Int): CompiledCode {
        val open = files[file]!!
        val compiled = open.prepareCompiledFile()
        val recompileStrategy = compiled.recompile(offset)

        return when (recompileStrategy) {
            NoChanges ->
                compiled.compiledCode(offset)
            Function ->
                compiled.recompileFunction(offset)
            File, Impossible ->
                open.compileIfChanged().prepareCompiledFile().compiledCode(offset)
        }
    }

    /**
     * Get parsed trees for all files on source path
     */
    fun all(): Collection<KtFile> =
            files.values().map { it.parseIfChanged().parsed!! }

    fun open(file: Path, content: String, version: Int) {
        val open = createMemoryFile(content, version, file)

        files += open
        open.compileIfNull()
    }

    fun close(file: Path) {
        files += openDiskFile(file)
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

            val edited = existing.edit(newText, document.version)
            files += edited
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

    private fun createMemoryFile(content: String, version: Int, file: Path): OpenFile {
        val parse = cp.compiler.createFile(file, content)

        return OpenFile(file, content, version, true, parse, version)
    }

    private fun openDiskFile(file: Path): OpenFile {
        val parse = cp.compiler.openFile(file)

        return OpenFile(file, parse.text, -1, false, parse, -1)
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
        for (open in files.values()) {
            if (open.open)
                open.compileIfChanged()
        }
    }

    fun createdOnDisk(file: Path) {
        changedOnDisk(file)
    }

    fun deletedOnDisk(file: Path) {
        if (isSource(file))
            files -= file
    }

    fun changedOnDisk(file: Path) {
        if (isSource(file))
            files += openDiskFile(file)
    }

    private fun isSource(file: Path): Boolean {
        val name = file.fileName.toString()

        return name.endsWith(".kt") || name.endsWith(".kts")
    }

    fun addWorkspaceRoot(root: Path) {
        val addSources = findSourceFiles(root)

        logAdded(addSources, root)

        for (file in addSources) {
            files += openDiskFile(file)
        }

        workspaceRoots += root
    }

    fun removeWorkspaceRoot(root: Path) {
        val rmSources = files.keys().filter { it.startsWith(root) }

        logRemoved(rmSources, root)

        files.removeAll(rmSources)
        workspaceRoots -= root
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
