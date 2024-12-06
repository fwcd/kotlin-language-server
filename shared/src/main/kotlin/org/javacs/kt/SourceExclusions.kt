package org.javacs.kt

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import org.javacs.kt.util.filePath

// TODO: Read exclusions from gitignore/settings.json/... instead of
// hardcoding them
class SourceExclusions(
    private val workspaceRoots: Collection<Path>,
    scriptsConfig: ScriptsConfiguration,
) {
    val excludedPatterns =
        (listOf(
            ".git",
            ".hg",
            ".svn", // Version control systems
            ".idea",
            ".idea_modules",
            ".vs",
            ".vscode",
            ".code-workspace",
            ".settings", // IDEs
            "bazel-*",
            "bin",
            "build",
            "node_modules",
        ) +
            when {
                !scriptsConfig.enabled -> listOf("*.kts")
                !scriptsConfig.buildScriptsEnabled -> listOf("*.gradle.kts")
                else -> emptyList()
            })

    private val exclusionMatchers = excludedPatterns
        .filter { !it.startsWith("!") }
        .map { FileSystems.getDefault().getPathMatcher("glob:$it") }

    fun walkIncluded(): Sequence<Path> =
        workspaceRoots.asSequence().flatMap { root ->
            root.toFile().walk().onEnter { isPathIncluded(it.toPath()) }.map { it.toPath() }
        }

    fun isURIIncluded(uri: URI) = uri.filePath?.let(this::isPathIncluded) ?: false

    fun isPathIncluded(file: Path): Boolean {
        if (!workspaceRoots.any { file.startsWith(it) }) {
            return false
        }

        val relativePaths = workspaceRoots
            .mapNotNull { if (file.startsWith(it)) it.relativize(file) else null }
            .flatten()

        // Check if we're in a target directory
        if (relativePaths.contains(Path.of("target"))) {
            val pathList = relativePaths.toList()
            val targetIndex = pathList.indexOf(Path.of("target"))

            // Allow only target directory itself or if next directory is generated-sources
            return pathList.size <= targetIndex + 1 ||
                pathList[targetIndex + 1] == Path.of("generated-sources")
        }

        // If path matches any exclusion pattern, exclude it
        if (exclusionMatchers.any { matcher ->
                relativePaths.any(matcher::matches)
            }) {
            return false
        }

        // Include paths outside target directory by default
        return true
    }

}

