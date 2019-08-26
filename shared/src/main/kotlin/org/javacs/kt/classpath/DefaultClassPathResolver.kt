package org.javacs.kt.classpath

import org.javacs.kt.LOG
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.Files
import java.nio.file.FileSystems

fun defaultClassPathResolver(workspaceRoots: Collection<Path>): ClassPathResolver =
    WithStdlibResolver(
        ShellClassPathResolver.global(workspaceRoots.firstOrNull())
            .or(workspaceRoots.asSequence().flatMap(::workspaceResolvers).joined)
    ).or(KotlinCliClassPathResolver.global())
     .or(BackupArtifactClassPathResolver)

/** Searches the workspace for all files that could provide classpath info. */
private fun workspaceResolvers(workspaceRoot: Path): Sequence<ClassPathResolver> {
    val ignored: List<PathMatcher> = ignoredPathPatterns(workspaceRoot.resolve(".gitignore"))
    return folderResolvers(workspaceRoot, workspaceRoot, ignored).asSequence()
}

/** Searches the folder for all build-files. */
private fun folderResolvers(workspaceRoot: Path, folder: Path, ignored: List<PathMatcher>): Collection<ClassPathResolver> {
    var resolvers = mutableListOf<ClassPathResolver>()

    for (file in Files.list(folder)) {
        // Only test whether non-ignored file is a build-file
        if (ignored.none { it.matches(workspaceRoot.relativize(file)) }) {
            val resolver = asClassPathProvider(file)
            if (resolver != null) {
                resolvers.add(resolver)
                break
            } else if (Files.isDirectory(file)) {
                resolvers.addAll(folderResolvers(workspaceRoot, file, ignored))
            }
        }
    }

    return resolvers
}

/** Tries to read glob patterns from a gitignore. */
private fun ignoredPathPatterns(path: Path): List<PathMatcher> =
    path.toFile()
        .takeIf { it.exists() }
        ?.readLines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?.let { it + listOf(
            // Patterns that are ignored by default
            ".git"
        ) }
        ?.mapNotNull { try {
            LOG.debug("Adding ignore pattern '{}' from {}", it, path)
            FileSystems.getDefault().getPathMatcher("glob:$it")
        } catch (e: Exception) {
            LOG.warn("Did not recognize gitignore pattern: '{}' ({})", it, e.message)
            null
        } }
        ?: emptyList<PathMatcher>()

/** Tries to create a classpath resolver from a file using as many sources as possible */
private fun asClassPathProvider(path: Path): ClassPathResolver? =
    MavenClassPathResolver.maybeCreate(path)
        ?: GradleClassPathResolver.maybeCreate(path)
        ?: ShellClassPathResolver.maybeCreate(path)
