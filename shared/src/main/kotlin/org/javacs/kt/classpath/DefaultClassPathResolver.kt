package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.jetbrains.exposed.sql.Database
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.nio.file.FileSystems

data class ResolverOptions(
    // Whether to use the compile classpath or the runtime classpath during classpath resolution
    val useCompileClasspath: Boolean,
) {
    companion object {
        fun default(): ResolverOptions {
            return ResolverOptions(useCompileClasspath = true)
        }
    }
}

val DefaultResolverOptions = ResolverOptions.default()

fun defaultClassPathResolver(
    workspaceRoots: Collection<Path>,
    db: Database? = null,
    resolverOptions: ResolverOptions = DefaultResolverOptions,
): ClassPathResolver {
    val childResolver = WithStdlibResolver(
        ShellClassPathResolver.global(workspaceRoots.firstOrNull())
            .or(workspaceRoots.asSequence().flatMap { workspaceResolvers(it, resolverOptions) }.joined)
    ).or(BackupClassPathResolver)

    return db?.let { CachedClassPathResolver(childResolver, it) } ?: childResolver
}

/** Searches the workspace for all files that could provide classpath info. */
private fun workspaceResolvers(workspaceRoot: Path, resolverOptions: ResolverOptions): Sequence<ClassPathResolver> {
    val ignored: List<PathMatcher> = ignoredPathPatterns(workspaceRoot, workspaceRoot.resolve(".gitignore"))
    return folderResolvers(workspaceRoot, ignored, resolverOptions).asSequence()
}

/** Searches the folder for all build-files. */
private fun folderResolvers(root: Path, ignored: List<PathMatcher>, resolverOptions: ResolverOptions): Collection<ClassPathResolver> =
    root.toFile()
        .walk()
        .onEnter { file -> ignored.none { it.matches(file.toPath()) } }
        .mapNotNull { asClassPathProvider(it.toPath(), resolverOptions) }
        .toList()

/** Tries to read glob patterns from a gitignore. */
private fun ignoredPathPatterns(root: Path, gitignore: Path): List<PathMatcher> =
    gitignore.toFile()
        .takeIf { it.exists() }
        ?.readLines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?.map { it.removeSuffix("/") }
        ?.let { it + listOf(
            // Patterns that are ignored by default
            ".git"
        ) }
        ?.mapNotNull { try {
            LOG.debug("Adding ignore pattern '{}' from {}", it, gitignore)
            FileSystems.getDefault().getPathMatcher("glob:$root**/$it")
        } catch (e: Exception) {
            LOG.warn("Did not recognize gitignore pattern: '{}' ({})", it, e.message)
            null
        } }
        ?: emptyList()

/** Tries to create a classpath resolver from a file using as many sources as possible */
private fun asClassPathProvider(path: Path, resolverOptions: ResolverOptions): ClassPathResolver? =
    MavenClassPathResolver.maybeCreate(path)
        ?: GradleClassPathResolver.maybeCreate(path, resolverOptions)
        ?: ShellClassPathResolver.maybeCreate(path)
