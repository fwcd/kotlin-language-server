package org.javacs.kt

import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil.convertLineSeparators
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
import java.util.stream.Collectors

private class SourceVersion(val content: String, val version: Int, val isTemporary: Boolean)

/**
 * Notify SourcePath whenever a file changes
 */
private class NotifySourcePath(private val sp: SourcePath) {
    private val files = mutableMapOf<URI, SourceVersion>()

    operator fun get(uri: URI): SourceVersion? = files[uri]

    operator fun set(uri: URI, source: SourceVersion) {
        val content = convertLineSeparators(source.content)

        files[uri] = source
        sp.put(uri, content, source.isTemporary)
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
        files[uri] = SourceVersion(content, version, isTemporary = !exclusions.isURIIncluded(uri))
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

            files[uri] = SourceVersion(newText, newVersion, existing.isTemporary)
        }
    }

    fun createdOnDisk(uri: URI) {
        changedOnDisk(uri)
    }

    fun deletedOnDisk(uri: URI) {
        if (isSource(uri))
            files.remove(uri)
    }

    fun changedOnDisk(uri: URI) {
        if (isSource(uri))
            files[uri] = readFromDisk(uri, files[uri]?.isTemporary ?: true)
                ?: throw KotlinLSException("Could not read source file '$uri' after being changed on disk")
    }

    private fun readFromDisk(uri: URI, temporary: Boolean): SourceVersion? = try {
        val content = contentProvider.contentOf(uri)
        SourceVersion(content, -1, isTemporary = temporary)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: IOException) {
        LOG.warn("Exception while reading source file {}", describeURI(uri))
        null
    }

    private fun isSource(uri: URI): Boolean {
        val path = uri.path
        return (path.endsWith(".kt") || path.endsWith(".kts")) && exclusions.isURIIncluded(uri)
    }

    fun addWorkspaceRoot(root: Path) {
        val addSources = findSourceFiles(root)

        logAdded(addSources, root)

        for (file in addSources) {
            readFromDisk(file.toUri(), temporary = false)?.let {
                files[file.toUri()] = it
            } ?: LOG.warn("Could not read source file '{}'", file)
        }

        workspaceRoots.add(root)
        updateExclusions()
    }

    fun removeWorkspaceRoot(root: Path) {
        val rmSources = files.keys.filter { it.filePath?.startsWith(root) ?: false }

        logRemoved(rmSources.map(Paths::get), root)

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
    for (character in 0 until range.start.character)
        writer.write(reader.read())

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
}

private fun findSourceFiles(root: Path): Set<Path> {
    val pattern = FileSystems.getDefault().getPathMatcher("glob:*.{kt,kts}")
    val exclusions = SourceExclusions(root)
    return Files.walk(root)
            .filter { pattern.matches(it.fileName) && exclusions.isPathIncluded(it) }
            .collect(Collectors.toSet())
}

private fun logAdded(sources: Collection<Path>, rootPath: Path?) {
    LOG.info("Adding {} under {} to source path", describeURIs(sources.map(Path::toUri)), rootPath)
}

private fun logRemoved(sources: Collection<Path>, rootPath: Path?) {
    LOG.info("Removing {} under {} to source path", describeURIs(sources.map(Path::toUri)), rootPath)
}
