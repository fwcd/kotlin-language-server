package org.javacs.kt

import org.javacs.kt.util.filePath
import java.io.File
import java.net.URI
import java.util.regex.PatternSyntaxException
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Path
import java.nio.file.Paths

class SourceExclusions(
    private val workspaceRoots: Collection<Path>,
    private val scriptsConfig: ScriptsConfiguration,
    private val exclusionsConfig: ExclusionsConfiguration,
) {
    val configuredExclusions = exclusionsConfig.excludePatterns.map { it.trim() }.filter { it.isNotEmpty() }
    val excludedPatterns = listOf(
        ".git", ".hg", ".svn",                                                      // Version control systems
        ".idea", ".idea_modules", ".vs", ".vscode", ".code-workspace", ".settings", // IDEs
        "bazel-*", "bin", "node_modules",                                           // Build systems
    ) + when {
        !scriptsConfig.enabled -> listOf("*.kts")
        !scriptsConfig.buildScriptsEnabled -> listOf("*.gradle.kts")
        else -> emptyList()
    } + configuredExclusions

    private val exclusionMatchers = excludedPatterns
        .map(::parseExcludePattern)
        .filterNotNull()

    private fun parseExcludePattern(pattern: String): PathMatcher? {
        fun warning(e: Exception) = LOG.warn("Did not recognize exclude pattern: '$pattern' (${e.message})")
        try {
            val normalizedPattern = pattern.removeSuffix("/").trim()
            val pathMatcher =
                // Takes inspiration from https://git-scm.com/docs/gitignore
                if (normalizedPattern.contains("/")) {
                    FileSystems.getDefault().getPathMatcher("glob:$normalizedPattern")
                } else {
                    PathMatcher { path -> path.any { FileSystems.getDefault().getPathMatcher("glob:$normalizedPattern").matches(it) } }
                }
            return pathMatcher
        } catch (e: IllegalArgumentException) {
            warning(e)
        } catch (e: PatternSyntaxException) {
            warning(e)
        } catch (e: UnsupportedOperationException) {
            warning(e)
        }
        return null
    }

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
                .any(matcher::matches)
        }
}
