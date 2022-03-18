package org.javacs.kt.classpath

import kotlinx.serialization.Serializable
import org.javacs.kt.storage.SetOfPathsAsStringSerializer
import org.javacs.kt.storage.Storage
import java.nio.file.Path

/** A classpath resolver that caches another resolver */
internal class CachedClassPathResolver(private val wrapped: ClassPathResolver, private val storage: Storage?) : ClassPathResolver {
    override val resolverType: String get() = "Cached + ${wrapped.resolverType}"

    private var cachedClassPath: ClasspathCache? = storage?.getObject("cachedClasspath")
    private var cachedBuildScriptClassPath: Set<Path>? = storage?.getObject("cachedBuildScriptClassPath", SetOfPathsAsStringSerializer)

    override val classpath: Set<ClassPathEntry> get() {
        cachedClassPath?.let { if (!dependenciesChanged()) return it.classpathEntries }

        val newClasspath = wrapped.classpath
        updateClasspathCache(ClasspathCache(newClasspath, false))

        return newClasspath
    }

    override val buildScriptClasspath: Set<Path> get() {
        cachedBuildScriptClassPath?.let { if (!dependenciesChanged()) return it }

        val newBuildScriptClasspath = wrapped.buildScriptClasspath

        updateBuildScriptClasspathCache(newBuildScriptClasspath)
        return newBuildScriptClasspath
    }

    override val classpathWithSources: Set<ClassPathEntry> get() {
        cachedClassPath?.let { if (!dependenciesChanged() && it.includesSources) return it.classpathEntries }

        val newClasspath = wrapped.classpathWithSources
        updateClasspathCache(ClasspathCache(newClasspath, true))

        return newClasspath
    }

    override fun getCurrentBuildFileVersion(): Long = wrapped.getCurrentBuildFileVersion()

    private fun updateClasspathCache(newClasspathCache: ClasspathCache) {
        storage?.setObject("cachedClasspath", newClasspathCache)
        storage?.setObject("cachedBuildFileVersion", getCurrentBuildFileVersion())
        cachedClassPath = newClasspathCache
    }

    private fun updateBuildScriptClasspathCache(newClasspath: Set<Path>) {
        storage?.setObject("cachedBuildScriptClassPath", newClasspath, SetOfPathsAsStringSerializer)
        storage?.setObject("cachedBuildFileVersion", getCurrentBuildFileVersion())
        cachedBuildScriptClassPath = newClasspath
    }

    private fun dependenciesChanged(): Boolean {
        return storage?.getObject<Long>("cachedBuildFileVersion") ?: 0 < wrapped.getCurrentBuildFileVersion()
    }
}

@Serializable
private data class ClasspathCache(
    val classpathEntries: Set<ClassPathEntry>,
    val includesSources: Boolean
)
