package org.javacs.kt

import java.nio.file.Path

// TODO: Read exclusions from gitignore/settings.json/... instead of
// hardcoding them
private val excludedFolders = listOf("bin", "build", "target")

class SourceExclusions {
	private val excludedBranches: Collection<Path>

	constructor(workspaceRoot: Path) : this(listOf(workspaceRoot)) {}

	constructor(workspaceRoots: Collection<Path>) {
		excludedBranches = workspaceRoots.flatMap { root -> excludedFolders.map { root.resolve(it) } }
	}

	fun isIncluded(file: Path) =
			excludedBranches.filter { file.startsWith(it) }
				.isEmpty()
}
