package org.javacs.kt

import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.Language
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.filePath
import org.javacs.kt.util.partitionAroundLast
import org.javacs.kt.util.describeURIs
import org.javacs.kt.util.describeURI
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import java.io.IOException
import java.io.FileNotFoundException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private class SourceVersion(val content: String, val version: Int, val language: Language?, val isTemporary: Boolean)

/**
 * Notify SourcePath whenever a file changes
 */
private class NotifySourcePath(private val sp: SourcePath) {
    private val files = mutableMapOf<URI, SourceVersion>()

    operator fun get(uri: URI): SourceVersion? = files[uri]

    operator fun set(uri: URI, source: SourceVersion) {
        val content = convertLineSeparators(source.content)

        files[uri] = source
        sp.put(uri, content, source.language, source.isTemporary)
    }

    fun remove(uri: URI) {
        files.remove(uri)
        sp.delete(uri)
    }

    fun removeIfTemporary(uri: URI): Boolean =
        if (sp.deleteIfTemporary(uri)) {
            files.remove(uri)
            true
        } else {
            false
        }

    fun removeAll(rm: Collection<URI>) {
        files -= rm

        rm.forEach(sp::delete)
    }

    val keys get() = files.keys
}

/**
 * Keep track of the text of all files in the workspace
 */
class SourceFiles(
    private val sp: SourcePath,
    private val contentProvider: URIContentProvider
) {
    private val workspaceRoots = mutableSetOf<Path>()
    private var exclusions = SourceExclusions(workspaceRoots)
    private val files = NotifySourcePath(sp)
    private val open = mutableSetOf<URI>()

    fun open(uri: URI, content: String, version: Int) {
        files[uri] = SourceVersion(content, version, languageOf(uri), isTemporary = !exclusions.isURIIncluded(uri))
        open.add(uri)
    }

    fun close(uri: URI) {
        if (uri in open) {
            open.remove(uri)
            val removed = files.removeIfTemporary(uri)

            if (!removed) {
                val disk = readFromDisk(uri, temporary = false)

                if (disk != null) {
                    files[uri] = disk
                } else {
                    files.remove(uri)
                }
            }
        }
    }

    fun edit(uri: URI, newVersion: Int, contentChanges: List<TextDocumentContentChangeEvent>) {
        if (exclusions.isURIIncluded(uri)) {
            val existing = files[uri]!!
            var newText = existing.content

            if (newVersion <= existing.version) {
                LOG.warn("Ignored {} version {}", describeURI(uri), newVersion)
                return
            }

            for (change in contentChanges) {
                if (change.range == null) newText = change.text
                else newText = patch(newText, change)
            }

            files[uri] = SourceVersion(newText, newVersion, existing.language, existing.isTemporary)
        }
    }

    fun createdOnDisk(uri: URI) {
        changedOnDisk(uri)
    }

    fun deletedOnDisk(uri: URI) {
        if (isSource(uri)) {
            files.remove(uri)
        }
    }

    fun changedOnDisk(uri: URI) {
        if (isSource(uri)) {
            files[uri] = readFromDisk(uri, files[uri]?.isTemporary ?: true)
                ?: throw KotlinLSException("Could not read source file '$uri' after being changed on disk")
        }
    }

    private fun readFromDisk(uri: URI, temporary: Boolean): SourceVersion? = try {
        val content = contentProvider.contentOf(uri)
        SourceVersion(content, -1, languageOf(uri), isTemporary = temporary)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: IOException) {
        LOG.warn("Exception while reading source file {}", describeURI(uri))
        null
    }

    private fun isSource(uri: URI): Boolean = exclusions.isURIIncluded(uri) && languageOf(uri) != null

    private fun languageOf(uri: URI): Language? {
        val fileName = uri.filePath?.fileName?.toString() ?: return null
        return when {
            fileName.endsWith(".kt") || fileName.endsWith(".kts") -> KotlinLanguage.INSTANCE
            else -> null
        }
    }

    fun addWorkspaceRoot(root: Path) {
        val addSources = findSourceFiles(root)

        logAdded(addSources, root)

        for (uri in addSources) {
            readFromDisk(uri, temporary = false)?.let {
                files[uri] = it
            } ?: LOG.warn("Could not read source file '{}'", uri.path)
        }

        workspaceRoots.add(root)
        updateExclusions()
    }

    fun removeWorkspaceRoot(root: Path) {
        val rmSources = files.keys.filter { it.filePath?.startsWith(root) ?: false }

        logRemoved(rmSources, root)

        files.removeAll(rmSources)
        workspaceRoots.remove(root)
        updateExclusions()
    }

    private fun updateExclusions() {
        exclusions = SourceExclusions(workspaceRoots)
    }

    fun isOpen(uri: URI): Boolean = (uri in open)
}

private fun patch(sourceText: String, change: TextDocumentContentChangeEvent): String {
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
    for (character in 0 until range.start.character) {
        writer.write(reader.read())
    }

    // Write replacement text
    writer.write(change.text)

    // Skip replaced text
    for (i in 0 until (range.end.line - range.start.line)) {
        reader.readLine()
    }
    if (range.start.line == range.end.line) {
        reader.skip((range.end.character - range.start.character).toLong())
    } else {
        reader.skip(range.end.character.toLong())
    }

    // Write remaining text
    while (true) {
        val next = reader.read()

        if (next == -1) return writer.toString()
        else writer.write(next)
    }
}

private fun findSourceFiles(root: Path): Set<URI> {
    val sourceMatcher = FileSystems.getDefault().getPathMatcher("glob:*.{kt,kts}")
    return SourceExclusions(root)
        .walkIncluded()
        .filter { sourceMatcher.matches(it.fileName) }
        .map(Path::toUri)
        .toSet()
}

private fun logAdded(sources: Collection<URI>, rootPath: Path?) {
    LOG.info("Adding {} under {} to source path", describeURIs(sources), rootPath)
}

private fun logRemoved(sources: Collection<URI>, rootPath: Path?) {
    LOG.info("Removing {} under {} to source path", describeURIs(sources), rootPath)
}
