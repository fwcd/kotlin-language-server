package org.javacs.kt

import org.javacs.kt.util.filePath
import org.javacs.kt.util.describeURI
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.CompositeBindingContext
import java.nio.file.Path
import java.nio.file.Paths
import java.net.URI

class SourcePath(
    private val cp: CompilerClassPath,
    private val contentProvider: URIContentProvider
) {
    private val files = mutableMapOf<URI, SourceFile>()

    private inner class SourceFile(
            val uri: URI,
            var content: String,
            val path: Path? = uri.filePath,
            var parsed: KtFile? = null,
            var compiledFile: KtFile? = null,
            var compiledContext: BindingContext? = null,
            var compiledContainer: ComponentProvider? = null,
            val isTemporary: Boolean = false // A temporary source file will not be returned by .all()
    ) {
        fun put(newContent: String) {
            content = newContent
        }

        fun parseIfChanged(): SourceFile {
            if (content != parsed?.text) {
                parsed = cp.compiler.createFile(content, path ?: Paths.get("sourceFile.virtual.kt"))
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
                LOG.debug("Compiling {}", path?.fileName)

                val (context, container) = cp.compiler.compileFile(parsed!!, allIncludingThis())
                compiledContext = context
                compiledContainer = container
                compiledFile = parsed
            }

            return this
        }

        fun prepareCompiledFile(): CompiledFile =
                parseIfChanged().compileIfNull().doPrepareCompiledFile()

        private fun doPrepareCompiledFile(): CompiledFile =
                CompiledFile(content, compiledFile!!, compiledContext!!, compiledContainer!!, allIncludingThis(), cp)

        private fun allIncludingThis(): Collection<KtFile> = parseIfChanged().let {
            if (isTemporary) (all().asSequence() + sequenceOf(parsed!!)).toList()
            else all()
        }
    }

    private fun sourceFile(uri: URI): SourceFile {
        if (uri !in files) {
            LOG.info("Adding temporary source file {} to source path", describeURI(uri))
            put(uri, contentProvider.contentOf(uri), temporary = true)
        }
        return files[uri]!!
    }

    fun put(uri: URI, content: String, temporary: Boolean = false) {
        assert(!content.contains('\r'))

        if (uri in files)
            sourceFile(uri).put(content)
        else
            files[uri] = SourceFile(uri, content, isTemporary = temporary)
    }

    fun deleteIfTemporary(uri: URI): Boolean =
        if (sourceFile(uri).isTemporary) {
            LOG.info("Deleting temporary source file {} from source path", describeURI(uri))
            delete(uri)
            true
        } else {
            false
        }

    fun delete(uri: URI) {
        files.remove(uri)
    }

    /**
     * Get the latest content of a file
     */
    fun content(uri: URI): String = sourceFile(uri).content

    fun parsedFile(uri: URI): KtFile = sourceFile(uri).parseIfChanged().parsed!!

    /**
     * Compile the latest version of a file
     */
    fun currentVersion(uri: URI): CompiledFile =
            sourceFile(uri).compileIfChanged().prepareCompiledFile()

    /**
     * Return whatever is the most-recent already-compiled version of `file`
     */
    fun latestCompiledVersion(uri: URI): CompiledFile =
            sourceFile(uri).prepareCompiledFile()

    /**
     * Compile changed files
     */
    fun compileFiles(all: Collection<URI>): BindingContext {
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
    fun all(includeHidden: Boolean = false): Collection<KtFile> =
            files.values
                .filterNot { includeHidden && it.isTemporary }
                .map { it.parseIfChanged().parsed!! }
}
