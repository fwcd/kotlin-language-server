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

    fun isURIIncluded(uri: URI) = uri.filePath?.let(this::isPathIncluded) ?: false

    fun isPathIncluded(file: Path): Boolean = workspaceRoots.any { file.startsWith(it) }
        && excludedFolders.none {
            workspaceRoots
                .mapNotNull { if (file.startsWith(it)) it.relativize(file) else null }
                .flatMap { it } // Extract path segments
                .any { segment -> segment.toString() == it }
        }
}
