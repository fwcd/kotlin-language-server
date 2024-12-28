package org.javacs.kt.classpath

import java.nio.file.Path

/** A classpath resolver that ensures another resolver contains the stdlib */
internal class WithStdlibResolver(private val wrapped: ClassPathResolver) : ClassPathResolver {
    override val resolverType: String get() = "Stdlib + ${wrapped.resolverType}"
    override val classpath: Set<ClassPathEntry> get() = wrapWithStdlibEntries(wrapped.classpath)
    override val classpathOrEmpty: Set<ClassPathEntry> get() = wrapWithStdlibEntries(wrapped.classpathOrEmpty)
    override val buildScriptClasspath: Set<Path> get() = wrapWithStdlib(wrapped.buildScriptClasspath)
    override val buildScriptClasspathOrEmpty: Set<Path> get() = wrapWithStdlib(wrapped.buildScriptClasspathOrEmpty)
    override val classpathWithSources: Set<ClassPathEntry> get() = wrapWithStdlibEntries(wrapped.classpathWithSources)
    override val currentBuildFileVersion: Long get() = wrapped.currentBuildFileVersion
}

private fun wrapWithStdlibEntries(paths: Set<ClassPathEntry>): Set<ClassPathEntry> {
    return wrapWithStdlib(paths.map { it.compiledJar }.toSet()).map { ClassPathEntry(it, paths.find { it1 -> it1.compiledJar == it }?.sourceJar) }.toSet()
}

private fun wrapWithStdlib(paths: Set<Path>): Set<Path> {
    // Ensure that there is exactly one kotlin-stdlib present, and/or exactly one of kotlin-stdlib-common, -jdk8, etc.
    val isStdlib: ((Path) -> Boolean) = {
        val pathString = it.toString()
        pathString.contains("kotlin-stdlib") && !pathString.contains("kotlin-stdlib-common")
    }

    val linkedStdLibs = paths.filter(isStdlib)
        .mapNotNull { StdLibItem.from(it) }
        .groupBy { it.key }
        .map { candidates ->
            // For each "kotlin-stdlib-blah", use the newest.  This may not be correct behavior if the project has lots of
            // conflicting dependencies, but in general should get enough of the stdlib loaded that we can display errors

            candidates.value.sortedWith(
                compareByDescending<StdLibItem> { it.major } then
                    compareByDescending { it.minor } then
                    compareByDescending { it.patch }
            ).first().path
        }

    val stdlibs = linkedStdLibs.ifEmpty {
        findKotlinStdlib()?.let { listOf(it) } ?: listOf()
    }

    return paths.filterNot(isStdlib).union(stdlibs)
}

private data class StdLibItem(
    val key: String,
    val major: Int,
    val minor: Int,
    val patch: Int,
    val path: Path
) {
    companion object {
        // Matches names like: "kotlin-stdlib-jdk7-1.2.51.jar"
        val parser = Regex("""(kotlin-stdlib(-[^-]+)?)(?:-(\d+)\.(\d+)\.(\d+))?\.jar""")

        fun from(path: Path) : StdLibItem? {
            return parser.matchEntire(path.fileName.toString())?.let { match ->
                StdLibItem(
                    key = match.groups[1]?.value ?: match.groups[0]?.value!!,
                    major = match.groups[3]?.value?.toInt() ?: 0,
                    minor = match.groups[4]?.value?.toInt() ?: 0,
                    patch = match.groups[5]?.value?.toInt() ?: 0,
                    path = path
                )
            }
        }
    }
}
