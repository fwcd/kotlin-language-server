package org.javacs.kt

import org.javacs.kt.RecompileStrategy.*
import org.javacs.kt.RecompileStrategy.Function
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
            var compiledContext: BindingContext? = null) {

        fun put(newContent: String) {
            content = newContent
        }

        fun parseIfChanged(): SourceFile {
            if (content != parsed?.text) {
                parsed = cp.compiler.createFile(file, content)
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
                LOG.info("Compiling ${file.fileName}")

                compiledContext = cp.compiler.compileFile(parsed!!, all())
                compiledFile = parsed
            }

            return this
        }

        fun prepareCompiledFile(): CompiledFile =
                parseIfChanged().compileIfNull().doPrepareCompiledFile()

        private fun doPrepareCompiledFile(): CompiledFile =
                CompiledFile(content, compiledFile!!, compiledContext!!, all(), cp)
    }

    fun put(file: Path, content: String) {
        assert(!content.contains('\r'))

        if (files.contains(file))
            files[file]!!.put(content)
        else
            files[file] = SourceFile(file, content)
    }

    fun delete(file: Path) {
        files.remove(file)
    }

    /**
     * Get the latest content of a file
     */
    fun content(file: Path): String = files[file]!!.content

    fun parsedFile(file: Path): KtFile = files[file]!!.parseIfChanged().parsed!!

    /**
     * Compile the latest version of a file
     */
    fun compiledFile(file: Path): Pair<KtFile, BindingContext> {
        val compiled = files[file]!!.compileIfChanged()

        return Pair(compiled.compiledFile!!, compiled.compiledContext!!)
    }

    /**
     * Compile changed files
     */
    fun compileFiles(all: Collection<Path>): BindingContext {
        // Figure out what has changed
        val sources = all.map { files[it]!! }
        val changed = sources.filter { it.content != it.compiledFile?.text }

        // Compile changed files
        val parse = changed.map { it.parseIfChanged().parsed!! }
        val compile = cp.compiler.compileFiles(parse, all())

        // Update cache
        for (f in changed) {
            f.compiledFile = f.parsed
            f.compiledContext = compile
        }

        // Combine with past compilations
        val combined = mutableListOf(compile)
        val same = sources - changed
        combined.addAll(same.map { it.compiledContext!! })

        return CompositeBindingContext.create(combined)
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
            files.values.map { it.parseIfChanged().parsed!! }
}