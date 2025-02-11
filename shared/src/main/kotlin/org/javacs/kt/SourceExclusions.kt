package org.javacs.kt

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import org.javacs.kt.util.filePath
import java.io.IOException

class SourceExclusions(
    private val workspaceRoots: Collection<Path>,
    scriptsConfig: ScriptsConfiguration,
) {
    private val defaultPatterns =
        listOf(
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
        )

    private val scriptPatterns =
        when {
            !scriptsConfig.enabled -> listOf("*.kts")
            !scriptsConfig.buildScriptsEnabled -> listOf("*.gradle.kts")
            else -> emptyList()
        }

    private val gitignorePatterns: List<String> = readGitignorePatterns()

    val excludedPatterns = defaultPatterns + scriptPatterns + gitignorePatterns

    private val exclusionMatchers =
        excludedPatterns
            .filter { !it.startsWith("!") } // Skip negated patterns for now
            .map { FileSystems.getDefault().getPathMatcher("glob:$it") }

    private fun readGitignorePatterns(): List<String> {
        return workspaceRoots
            .flatMap { root ->
                val gitignore = root.resolve(".gitignore")
                if (Files.exists(gitignore)) {
                    try {
                        Files.readAllLines(gitignore)
                            .asSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                            .map { it.removeSuffix("/") }
                            .toList()
                            .also { patterns ->
                                LOG.debug("Read {} patterns from {}", patterns.size, gitignore)
                            }
                    } catch (e: IOException) {
                        LOG.warn("Could not read .gitignore at $gitignore: ${e.message}")
                        emptyList()
                    } catch (e: SecurityException) {
                        LOG.warn("Security error reading .gitignore at $gitignore: ${e.message}")
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
            .distinct() // Remove duplicates across workspace roots
    }

    fun walkIncluded(): Sequence<Path> =
        workspaceRoots.asSequence().flatMap { root ->
            root.toFile().walk().onEnter { isPathIncluded(it.toPath()) }.map { it.toPath() }
        }

    fun isURIIncluded(uri: URI) = uri.filePath?.let(this::isPathIncluded) ?: false

    fun isPathIncluded(file: Path): Boolean {
        if (!workspaceRoots.any { file.startsWith(it) }) {
            return false
        }

        val relativePaths =
            workspaceRoots
                .mapNotNull { if (file.startsWith(it)) it.relativize(file) else null }
                .flatten()

        val isIncluded =
            when {
                // Check if we're in a target directory
                relativePaths.contains(Path.of("target")) -> {
                    val pathList = relativePaths.toList()
                    val targetIndex = pathList.indexOf(Path.of("target"))
                    // Allow only target directory itself or if next directory is generated-sources
                    pathList.size <= targetIndex + 1 ||
                        pathList[targetIndex + 1] == Path.of("generated-sources")
                }
                // Check exclusion patterns
                exclusionMatchers.any { matcher -> relativePaths.any(matcher::matches) } -> false
                // Include paths outside target directory by default
                else -> true
            }

        return isIncluded
    }
}
