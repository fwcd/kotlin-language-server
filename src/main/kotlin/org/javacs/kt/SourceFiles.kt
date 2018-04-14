package org.javacs.kt

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

private class SourceVersion(val content: String, val version: Int, val open: Boolean)

private class NotifySourcePath(private val sourcePath: SourcePath) {
    private val files = mutableMapOf<Path, SourceVersion>()

    operator fun get(file: Path): SourceVersion? = files[file]

    operator fun set(file: Path, source: SourceVersion) {
        val content = convertLineSeparators(source.content)
        
        files[file] = source
        sourcePath.put(file, content, source.version, source.open)
    }

    fun remove(file: Path) {
        files.remove(file)
        sourcePath.delete(file)
    }

    fun removeAll(rm: Collection<Path>) {
        files -= rm

        rm.forEach(sourcePath::delete)
    }

    val keys get() = files.keys
}

class SourceFiles(private val sourcePath: SourcePath) {
    private val workspaceRoots = mutableSetOf<Path>()
    private val files = NotifySourcePath(sourcePath)

    fun open(file: Path, content: String, version: Int) {
        files[file] = SourceVersion(content, version, true)
    }

    fun close(file: Path) {
        files[file] = readFromDisk(file)
    }

    fun edit(params: DidChangeTextDocumentParams) {
        val document = params.textDocument
        val file = Paths.get(URI.create(document.uri))
        val existing = files[file]!!
        var newText = existing.content

        if (document.version <= existing.version) {
            LOG.warning("Ignored ${file.fileName} version ${document.version}")
            return
        }

        for (change in params.contentChanges) {
            if (change.range == null) newText = change.text
            else newText = patch(newText, change)
        }

        files[file] = SourceVersion(newText, document.version, true)
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
            files[file] = readFromDisk(file)
    }

    private fun readFromDisk(file: Path): SourceVersion {
        val content = Files.readAllLines(file).joinToString("\n")

        return SourceVersion(content, -1, false)
    }

    private fun isSource(file: Path): Boolean {
        val name = file.fileName.toString()

        return name.endsWith(".kt") || name.endsWith(".kts")
    }

    fun addWorkspaceRoot(root: Path) {
        val addSources = findSourceFiles(root)

        logAdded(addSources, root)

        for (file in addSources) {
            files[file] = readFromDisk(file)
        }

        workspaceRoots.add(root)
    }

    fun removeWorkspaceRoot(root: Path) {
        val rmSources = files.keys.filter { it.startsWith(root) }

        logRemoved(rmSources, root)

        files.removeAll(rmSources)
        workspaceRoots.remove(root)
    }

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
