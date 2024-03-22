package org.javacs.kt.classpath

import org.javacs.kt.LOG
import java.nio.file.Path
import kotlin.math.max

/** A source for creating class paths */
interface ClassPathResolver {
    val resolverType: String

    val classpath: ClassPathResult // may throw exceptions
    val classpathOrEmpty: ClassPathResult // does not throw exceptions
        get() = try {
            classpath
        } catch (e: Exception) {
            LOG.warn("Could not resolve classpath using {}: {}", resolverType, e.message)
            ClassPathResult(emptySet<ClassPathEntry>())
        }

    val buildScriptClasspath: Set<Path>
        get() = emptySet<Path>()
    val buildScriptClasspathOrEmpty: Set<Path>
        get() = try {
            buildScriptClasspath
        } catch (e: Exception) {
            LOG.warn("Could not resolve buildscript classpath using {}: {}", resolverType, e.message)
            emptySet<Path>()
        }

    val classpathWithSources: ClassPathResult get() = classpath

    /**
     * This should return the current build file version.
     * It usually translates to the file's lastModified time.
     * Resolvers that don't have a build file use the default (i.e., 1).
     * We use 1, because this will prevent any attempt to cache non cacheable resolvers
     * (see [CachedClassPathResolver.dependenciesChanged]).
     */
    val currentBuildFileVersion: Long
        get() = 1L

    companion object {
        /** A default empty classpath implementation */
        val empty = object : ClassPathResolver {
            override val resolverType = "[]"
            override val classpath = ClassPathResult(emptySet<ClassPathEntry>())
        }
    }
}

val Sequence<ClassPathResolver>.joined get() = fold(ClassPathResolver.empty) { accum, next -> accum + next }

val Collection<ClassPathResolver>.joined get() = fold(ClassPathResolver.empty) { accum, next -> accum + next }

/** Combines two classpath resolvers. */
operator fun ClassPathResolver.plus(other: ClassPathResolver): ClassPathResolver = UnionClassPathResolver(this, other)

/** Uses the left-hand classpath if not empty, otherwise uses the right. */
infix fun ClassPathResolver.or(other: ClassPathResolver): ClassPathResolver = FirstNonEmptyClassPathResolver(this, other)

/** The union of two class path resolvers. */
internal class UnionClassPathResolver(val lhs: ClassPathResolver, val rhs: ClassPathResolver) : ClassPathResolver {
    override val resolverType: String get() = "(${lhs.resolverType} + ${rhs.resolverType})"
    override val classpath get() = ClassPathResult(lhs.classpath.entries + rhs.classpath.entries)
    override val classpathOrEmpty get() = ClassPathResult(lhs.classpathOrEmpty.entries + rhs.classpathOrEmpty.entries)
    override val buildScriptClasspath get() = lhs.buildScriptClasspath + rhs.buildScriptClasspath
    override val buildScriptClasspathOrEmpty get() = lhs.buildScriptClasspathOrEmpty + rhs.buildScriptClasspathOrEmpty
    override val classpathWithSources get() = ClassPathResult(lhs.classpathWithSources.entries + rhs.classpathWithSources.entries)
    override val currentBuildFileVersion: Long get() = max(lhs.currentBuildFileVersion, rhs.currentBuildFileVersion)
}

internal class FirstNonEmptyClassPathResolver(val lhs: ClassPathResolver, val rhs: ClassPathResolver) : ClassPathResolver {
    override val resolverType: String get() = "(${lhs.resolverType} or ${rhs.resolverType})"
    override val classpath get() = ClassPathResult(lhs.classpath.entries.takeIf { it.isNotEmpty() } ?: rhs.classpath.entries)
    override val classpathOrEmpty get() = ClassPathResult(lhs.classpathOrEmpty.entries.takeIf { it.isNotEmpty() } ?: rhs.classpathOrEmpty.entries)
    override val buildScriptClasspath get() = lhs.buildScriptClasspath.takeIf { it.isNotEmpty() } ?: rhs.buildScriptClasspath
    override val buildScriptClasspathOrEmpty get() = lhs.buildScriptClasspathOrEmpty.takeIf { it.isNotEmpty() } ?: rhs.buildScriptClasspathOrEmpty
    override val classpathWithSources get() = ClassPathResult(lhs.classpathWithSources.entries.takeIf {
        it.isNotEmpty()
    } ?: rhs.classpathWithSources.entries)
    override val currentBuildFileVersion: Long get() = max(lhs.currentBuildFileVersion, rhs.currentBuildFileVersion)
}
