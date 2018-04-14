package org.javacs.kt

import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

private class SourceVersion(val content: String, val version: Int, val open: Boolean)

/**
 * Notify SourcePath whenever a file changes
 */
private class NotifySourcePath(private val sp: SourcePath) {
    private val files = mutableMapOf<Path, SourceVersion>()

    operator fun get(file: Path): SourceVersion? = files[file]

    operator fun set(file: Path, source: SourceVersion) {
        val content = convertLineSeparators(source.content)
        
        files[file] = source
        sp.put(file, content)
    }

    fun remove(file: Path) {
        files.remove(file)
        sp.delete(file)
    }

    fun removeAll(rm: Collection<Path>) {
        files -= rm

        rm.forEach(sp::delete)
    }

    val keys get() = files.keys
}

/**
 * Keep track of the text of all files in the workspace
 */
class SourceFiles(private val sp: SourcePath) {
    private val workspaceRoots = mutableSetOf<Path>()
    private val files = NotifySourcePath(sp)

    fun open(file: Path, content: String, version: Int) {
        files[file] = SourceVersion(content, version, true)
    }

    fun close(file: Path) {
        val disk = readFromDisk(file)

        if (disk != null)
            files[file] = disk
        else
            files.remove(file)
    }

    fun edit(file: Path, newVersion: Int, contentChanges: List<TextDocumentContentChangeEvent>) {
        val existing = files[file]!!
        var newText = existing.content

        if (newVersion <= existing.version) {
            LOG.warning("Ignored ${file.fileName} version $newVersion")
            return
        }

        for (change in contentChanges) {
            if (change.range == null) newText = change.text
            else newText = patch(newText, change)
        }

        files[file] = SourceVersion(newText, newVersion, true)
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
            files[file] = readFromDisk(file)!!
    }

    private fun readFromDisk(file: Path): SourceVersion? {
        if (!Files.exists(file)) return null 

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
            files[file] = readFromDisk(file)!!
        }

        workspaceRoots.add(root)
    }

    fun removeWorkspaceRoot(root: Path) {
        val rmSources = files.keys.filter { it.startsWith(root) }

        logRemoved(rmSources, root)

        files.removeAll(rmSources)
        workspaceRoots.remove(root)
    }

    fun isOpen(file: Path): Boolean = files[file]!!.open
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
    LOG.info("Adding ${describeFiles(sources)} under $rootPath to source path")
}

private fun logRemoved(sources: Collection<Path>, rootPath: Path?) {
    LOG.info("Removing ${describeFiles(sources)} under $rootPath to source path")
}

fun describeFiles(files: Collection<Path>): String {
    return if (files.isEmpty()) "0 files"
    else if (files.size > 5) "${files.size} files"
    else files.map { it.fileName }.joinToString(", ")
}