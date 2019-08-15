package org.javacs.kt

import java.nio.file.Path

// TODO: Read exclusions from gitignore/settings.json/... instead of
// hardcoding them
class SourceExclusions(private val workspaceRoots: Collection<Path>) {
	private val excludedFolders = listOf("bin", "build", "target", "node_modules")

	constructor(workspaceRoot: Path) : this(listOf(workspaceRoot)) {}

    fun isIncluded(file: Path) =
        excludedFolders.none {
            workspaceRoots.map { it.relativize(file) }
                .flatMap { it } // Extract path segments
                .any { segment -> segment.toString() == it }
        }
}
