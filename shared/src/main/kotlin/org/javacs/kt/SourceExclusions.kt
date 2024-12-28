package org.javacs.kt

import org.javacs.kt.util.filePath
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

// TODO: Read exclusions from gitignore/settings.json/... instead of
// hardcoding them
class SourceExclusions(
    private val workspaceRoots: Collection<Path>,
    private val scriptsConfig: ScriptsConfiguration
) {
    val excludedPatterns = (listOf(
        ".git", ".hg", ".svn",                                                      // Version control systems
        ".idea", ".idea_modules", ".vs", ".vscode", ".code-workspace", ".settings", // IDEs
        "bazel-*", "bin", "build", "node_modules", "target",                        // Build systems
    ) + when {
        !scriptsConfig.enabled -> listOf("*.kts")
        !scriptsConfig.buildScriptsEnabled -> listOf("*.gradle.kts")
        else -> emptyList()
    })

    private val exclusionMatchers = excludedPatterns
        .map { FileSystems.getDefault().getPathMatcher("glob:$it") }

    /** Finds all non-excluded files recursively. */
    fun walkIncluded(): Sequence<Path> = workspaceRoots.asSequence().flatMap { root ->
        root.toFile()
            .walk()
            .onEnter { isPathIncluded(it.toPath()) }
            .map { it.toPath() }
    }

    /** Tests whether the given URI is not excluded. */
    fun isURIIncluded(uri: URI) = uri.filePath?.let(this::isPathIncluded) ?: false

    /** Tests whether the given path is not excluded. */
    fun isPathIncluded(file: Path): Boolean = workspaceRoots.any { file.startsWith(it) }
        && exclusionMatchers.none { matcher ->
            workspaceRoots
                .mapNotNull { if (file.startsWith(it)) it.relativize(file) else null }
                .flatMap { it } // Extract path segments
                .any(matcher::matches)
        }
}
