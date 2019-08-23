package org.javacs.kt

import org.javacs.kt.util.filePath
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths

// TODO: Read exclusions from gitignore/settings.json/... instead of
// hardcoding them
class SourceExclusions(private val workspaceRoots: Collection<Path>) {
	private val excludedFolders = listOf("bin", "build", "target", "node_modules")

	constructor(workspaceRoot: Path) : this(listOf(workspaceRoot)) {}

    fun isURIIncluded(uri: URI) = uri.filePath?.let(this::isPathIncluded) ?: true

    fun isPathIncluded(file: Path) =
        excludedFolders.none {
            workspaceRoots
                .mapNotNull { try { it.relativize(file) } catch (e: IllegalArgumentException) { null } }
                .flatMap { it } // Extract path segments
                .any { segment -> segment.toString() == it }
        }
}
