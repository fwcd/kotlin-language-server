package org.javacs.kt

import org.javacs.kt.compiler.CompilationKind
import org.javacs.kt.util.fileExtension
import org.javacs.kt.util.filePath
import org.javacs.kt.util.describeURI
import com.intellij.lang.Language
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.CompositeBindingContext
import kotlin.concurrent.withLock
import java.nio.file.Path
import java.nio.file.Paths
import java.net.URI
import java.util.concurrent.locks.ReentrantLock

class SourcePath(
    private val cp: CompilerClassPath,
    private val contentProvider: URIContentProvider
) {
    private val files = mutableMapOf<URI, SourceFile>()
    private val parseDataWriteLock = ReentrantLock()

    var beforeCompileCallback: () -> Unit = {}

    private inner class SourceFile(
        val uri: URI,
        var content: String,
        val path: Path? = uri.filePath,
        var parsed: KtFile? = null,
        var compiledFile: KtFile? = null,
        var compiledContext: BindingContext? = null,
        var compiledContainer: ComponentProvider? = null,
        val language: Language? = null,
        val isTemporary: Boolean = false // A temporary source file will not be returned by .all()
    ) {
        val extension: String? = uri.fileExtension ?: language?.associatedFileType?.defaultExtension
        val isScript: Boolean = extension == "kts"
        val kind: CompilationKind =
            if (path?.fileName?.toString()?.endsWith(".gradle.kts") ?: false) CompilationKind.BUILD_SCRIPT
            else CompilationKind.DEFAULT

        fun put(newContent: String) {
            content = newContent
        }

        fun clean() {
            parsed = null
            compiledFile = null
            compiledContext = null
            compiledContainer = null
        }

        fun parse() {
            // TODO: Create PsiFile using the stored language instead
            parsed = cp.compiler.createKtFile(content, path ?: Paths.get("sourceFile.virtual.$extension"), kind)
        }

        fun parseIfChanged() {
            if (content != parsed?.text) {
                parse()
            }
        }

        fun compileIfNull() = parseIfChanged().apply { doCompileIfNull() }

        private fun doCompileIfNull() {
            if (compiledFile == null) {
                doCompileIfChanged()
            }
        }

        fun compileIfChanged() = parseIfChanged().apply { doCompileIfChanged() }

        fun compile() = parse().apply { doCompile() }

        private fun doCompile() {
            LOG.debug("Compiling {}", path?.fileName)

            val (context, container) = cp.compiler.compileKtFile(parsed!!, allIncludingThis(), kind)
            parseDataWriteLock.withLock {
                compiledContext = context
                compiledContainer = container
                compiledFile = parsed
            }
        }

        private fun doCompileIfChanged() {
            if (parsed?.text != compiledFile?.text) {
                doCompile()
            }
        }

        fun prepareCompiledFile(): CompiledFile =
                parseIfChanged().apply { compileIfNull() }.let { doPrepareCompiledFile() }

        private fun doPrepareCompiledFile(): CompiledFile =
                CompiledFile(content, compiledFile!!, compiledContext!!, compiledContainer!!, allIncludingThis(), cp, isScript, kind)

        private fun allIncludingThis(): Collection<KtFile> = parseIfChanged().let {
            if (isTemporary) (all().asSequence() + sequenceOf(parsed!!)).toList()
            else all()
        }
    }

    private fun sourceFile(uri: URI): SourceFile {
        if (uri !in files) {
            // Fallback solution, usually *all* source files
            // should be added/opened through SourceFiles
            LOG.warn("Requested source file {} is not on source path, this is most likely a bug. Adding it now temporarily...", describeURI(uri))
            put(uri, contentProvider.contentOf(uri), null, temporary = true)
        }
        return files[uri]!!
    }

    fun put(uri: URI, content: String, language: Language?, temporary: Boolean = false) {
        assert(!content.contains('\r'))

        if (temporary) {
            LOG.info("Adding temporary source file {} to source path", describeURI(uri))
        }

        if (uri in files) {
            sourceFile(uri).put(content)
        } else {
            files[uri] = SourceFile(uri, content, language = language, isTemporary = temporary)
        }
    }

    fun deleteIfTemporary(uri: URI): Boolean =
        if (sourceFile(uri).isTemporary) {
            LOG.info("Removing temporary source file {} from source path", describeURI(uri))
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

    fun parsedFile(uri: URI): KtFile = sourceFile(uri).apply { parseIfChanged() }.parsed!!

    /**
     * Compile the latest version of a file
     */
    fun currentVersion(uri: URI): CompiledFile =
            sourceFile(uri).apply { compileIfChanged() }.prepareCompiledFile()

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
        val allChanged = sources.filter { it.content != it.compiledFile?.text }
        val (changedBuildScripts, changedSources) = allChanged.partition { it.kind == CompilationKind.BUILD_SCRIPT }

        // Compile changed files
        fun compileAndUpdate(changed: List<SourceFile>, kind: CompilationKind): BindingContext? {
            if (changed.isEmpty()) return null
            val parse = changed.associateWith { it.apply { parseIfChanged() }.parsed!! }
            val allFiles = all()
            beforeCompileCallback.invoke()
            val (context, container) = cp.compiler.compileKtFiles(parse.values, allFiles, kind)

            // Update cache
            for ((f, parsed) in parse) {
                parseDataWriteLock.withLock {
                    if (f.parsed == parsed) {
                        //only updated if the parsed file didn't change:
                        f.compiledFile = parsed
                        f.compiledContext = context
                        f.compiledContainer = container
                    }
                }
            }

            return context
        }

        val buildScriptsContext = compileAndUpdate(changedBuildScripts, CompilationKind.BUILD_SCRIPT)
        val sourcesContext = compileAndUpdate(changedSources, CompilationKind.DEFAULT)

        // Combine with past compilations
        val same = sources - allChanged
        val combined = listOf(buildScriptsContext, sourcesContext).filterNotNull() + same.map { it.compiledContext!! }

        return CompositeBindingContext.create(combined)
    }

    /**
     * Recompiles all source files that are initialized.
     */
    fun refresh() {
        val initialized = files.values.any { it.parsed != null }
        if (initialized) {
            LOG.info("Refreshing source path")
            files.values.forEach { it.clean() }
            files.values.forEach { it.compile() }
        }
    }

    /**
     * Get parsed trees for all .kt files on source path
     */
    fun all(includeHidden: Boolean = false): Collection<KtFile> =
            files.values
                .filter { includeHidden || !it.isTemporary }
                .map { it.apply { parseIfChanged() }.parsed!! }
}
