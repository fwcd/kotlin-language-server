package org.javacs.kt

import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.CompositeBindingContext
import java.nio.file.Path

class SourcePath(private val cp: CompilerClassPath) {
    private val files = mutableMapOf<Path, SourceFile>()

    private inner class SourceFile(
            val file: Path,
            var content: String,
            var parsed: KtFile? = null,
            var compiledFile: KtFile? = null,
            var compiledContext: BindingContext? = null,
            var compiledContainer: ComponentProvider? = null) {

        fun put(newContent: String) {
            content = newContent
        }

        fun parseIfChanged(): SourceFile {
            if (content != parsed?.text) {
                parsed = cp.compiler.createFile(content, file)
            }

            return this
        }

        fun compileIfNull(): SourceFile =
                parseIfChanged().doCompileIfNull()

        private fun doCompileIfNull(): SourceFile =
            if (compiledFile == null)
                doCompileIfChanged()
            else
                this

        fun compileIfChanged(): SourceFile =
                parseIfChanged().doCompileIfChanged()

        private fun doCompileIfChanged(): SourceFile {
            if (parsed?.text != compiledFile?.text) {
                LOG.debug("Compiling {}", file.fileName)

                val (context, container) = cp.compiler.compileFile(parsed!!, all())
                compiledContext = context
                compiledContainer = container
                compiledFile = parsed
            }

            return this
        }

        fun prepareCompiledFile(): CompiledFile =
                parseIfChanged().compileIfNull().doPrepareCompiledFile()

        private fun doPrepareCompiledFile(): CompiledFile =
                CompiledFile(content, compiledFile!!, compiledContext!!, compiledContainer!!, all(), cp)
    }

    private fun sourceFile(file: Path): SourceFile {
        if (file !in files) {
            LOG.warn("File {} is not on SourcePath, adding it now...", file)
            put(file, file.toFile().readText())
        }
        return files[file]!!
    }

    fun put(file: Path, content: String) {
        assert(!content.contains('\r'))

        if (file in files)
            sourceFile(file).put(content)
        else
            files[file] = SourceFile(file, content)
    }

    fun delete(file: Path) {
        files.remove(file)
    }

    /**
     * Get the latest content of a file
     */
    fun content(file: Path): String = sourceFile(file).content

    fun parsedFile(file: Path): KtFile = sourceFile(file).parseIfChanged().parsed!!

    /**
     * Compile the latest version of a file
     */
    fun currentVersion(file: Path): CompiledFile =
            sourceFile(file).compileIfChanged().prepareCompiledFile()

    /**
     * Return whatever is the most-recent already-compiled version of `file`
     */
    fun latestCompiledVersion(file: Path): CompiledFile =
            sourceFile(file).prepareCompiledFile()

    /**
     * Compile changed files
     */
    fun compileFiles(all: Collection<Path>): BindingContext {
        // Figure out what has changed
        val sources = all.map { files[it]!! }
        val changed = sources.filter { it.content != it.compiledFile?.text }

        // Compile changed files
        val parse = changed.map { it.parseIfChanged().parsed!! }
        val (context, container) = cp.compiler.compileFiles(parse, all())

        // Update cache
        for (f in changed) {
            f.compiledFile = f.parsed
            f.compiledContext = context
            f.compiledContainer = container
        }

        // Combine with past compilations
        val combined = mutableListOf(context)
        val same = sources - changed
        combined.addAll(same.map { it.compiledContext!! })

        return CompositeBindingContext.create(combined)
    }

    /**
     * Get parsed trees for all .kt files on source path
     */
    fun all(): Collection<KtFile> =
            files.values.map { it.parseIfChanged().parsed!! }
}
