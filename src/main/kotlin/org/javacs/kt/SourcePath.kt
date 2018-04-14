package org.javacs.kt

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import org.javacs.kt.RecompileStrategy.*
import org.javacs.kt.RecompileStrategy.Function
import org.javacs.kt.diagnostic.KotlinDiagnostic
import org.javacs.kt.diagnostic.convertDiagnostic
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.nio.file.Path

class SourcePath(private val cp: CompilerClassPath) {
    private val files = SourceFiles()
    private var client: LanguageClient? = null

    /**
     * Prevents concurrent threads from reversing edits to open files
     */
    private class SourceFiles {
        private val files = mutableMapOf<Path, OpenFile>()

        operator fun get(file: Path): OpenFile {
            synchronized(files) {
                return files[file]!!
            }
        }

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

        fun values(): List<OpenFile> {
            synchronized(files) {
                return files.values.asSequence().toList()
            }
        }
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

        fun put(newContent: String, newVersion: Int, open: Boolean): OpenFile {
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

                return result
            }
            else return this
        }

        fun prepareCompiledFile(): CompiledFile =
                parseIfChanged().compileIfNull().doPrepareCompiledFile()

        private fun doPrepareCompiledFile(): CompiledFile =
                CompiledFile(content, compiledFile!!, compiledContext!!, all(), cp)
    }

    fun put(file: Path, content: String, version: Int, open: Boolean) {
        assert(!content.contains('\r'))

        if (file.contains(file))
            files[file].put(content, version, open)
        else
            files += OpenFile(file, content, version, open)

        if (!open)
            clearDiagnostics(file)

        lintLater()
    }

    fun delete(file: Path) {
        files -= file
        clearDiagnostics(file)
        // TODO flag all dependent files for linting
    }

    /**
     * Get the latest content of a file
     */
    fun content(file: Path): String =
            files[file].content

    fun parsedFile(file: Path): KtFile = files[file].parseIfChanged().parsed!!

    /**
     * Compile the latest version of a file
     */
    fun compiledFile(file: Path): Pair<KtFile, BindingContext> {
        val compiled = files[file].compileIfChanged()

        return Pair(compiled.compiledFile!!, compiled.compiledContext!!)
    }

    /**
     * Compile the latest version of the region around `offset`
     */
    fun compiledCode(file: Path, offset: Int): CompiledCode {
        val open = files[file]
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

    fun connect(client: LanguageClient) {
        this.client = client
    }

    var lintCount = 0

    private fun reportDiagnostics(open: Set<Path>, kotlinDiagnostics: List<KotlinDiagnostic>) {
        val langServerDiagnostics = kotlinDiagnostics.flatMap(::convertDiagnostic)
        val byFile = langServerDiagnostics.groupBy({ it.first }, { it.second })

        for ((file, diagnostics) in byFile) {
            if (open.contains(file)) {
                client!!.publishDiagnostics(PublishDiagnosticsParams(file.toUri().toString(), diagnostics))

                LOG.info("Reported ${diagnostics.size} diagnostics in $file")
            }
            else LOG.info("Ignore ${diagnostics.size} diagnostics in $file because it's not open")
        }

        val noErrors = open - byFile.keys
        for (file in noErrors) {
            clearDiagnostics(file)

            LOG.info("No diagnostics in $file")
        }

        // LOG.log(Level.WARNING, "LINT", Exception())

        lintCount++
    }

    private fun clearDiagnostics(file: Path) {
        if (client != null)
            client!!.publishDiagnostics(PublishDiagnosticsParams(file.toUri().toString(), listOf()))
    }

    val debounceLint = Debounce(1.0)

    private fun lintLater() {
        debounceLint.submit(::doLint)
    }

    private fun doLint() {
        val open = files.values().filter { it.open }
        val same = open.filter { it.version == it.compiledVersion }
        val changed = open.filter { it.version != it.compiledVersion }
        val parsed = changed.map { it.parseIfChanged().parsed!! }
        // TODO save this info in each OpenFile
        val compile = cp.compiler.compileFiles(parsed, all())
        val errs = compile.diagnostics.toList()

        for (f in same) reportDiagnostics(setOf(f.file), f.compiledContext!!.diagnostics.toList())
        reportDiagnostics(changed.map { it.file }.toSet(), errs)
    }

    fun compileFiles(files: Collection<KtFile>): BindingContext =
            cp.compiler.compileFiles(files, all())
}