package org.javacs.kt.classpath

import org.javacs.kt.LOG
import java.nio.file.Path

data class ClassPathEntry(val compiledJar: Path, val sourceJar: Path?)

/** A source for creating class paths */
interface ClassPathResolver {
    val resolverType: String

    val classpath: Set<ClassPathEntry> // may throw exceptions
    val classpathOrEmpty: Set<ClassPathEntry> // does not throw exceptions
        get() = try {
            classpath
        } catch (e: Exception) {
            LOG.warn("Could not resolve classpath using {}: {}", resolverType, e.message)
            emptySet<ClassPathEntry>()
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

    val classpathWithSources: Set<ClassPathEntry> get() = classpath

    companion object {
        /** A default empty classpath implementation */
        val empty = object : ClassPathResolver {
            override val resolverType = "[]"
            override val classpath = emptySet<ClassPathEntry>()
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
    override val classpath get() = lhs.classpath + rhs.classpath
    override val classpathOrEmpty get() = lhs.classpathOrEmpty + rhs.classpathOrEmpty
    override val buildScriptClasspath get() = lhs.buildScriptClasspath + rhs.buildScriptClasspath
    override val buildScriptClasspathOrEmpty get() = lhs.buildScriptClasspathOrEmpty + rhs.buildScriptClasspathOrEmpty
    override val classpathWithSources = lhs.classpathWithSources + rhs.classpathWithSources
}

internal class FirstNonEmptyClassPathResolver(val lhs: ClassPathResolver, val rhs: ClassPathResolver) : ClassPathResolver {
    override val resolverType: String get() = "(${lhs.resolverType} or ${rhs.resolverType})"
    override val classpath get() = lhs.classpath.takeIf { it.isNotEmpty() } ?: rhs.classpath
    override val classpathOrEmpty get() = lhs.classpathOrEmpty.takeIf { it.isNotEmpty() } ?: rhs.classpathOrEmpty
    override val buildScriptClasspath get() = lhs.buildScriptClasspath.takeIf { it.isNotEmpty() } ?: rhs.buildScriptClasspath
    override val buildScriptClasspathOrEmpty get() = lhs.buildScriptClasspathOrEmpty.takeIf { it.isNotEmpty() } ?: rhs.buildScriptClasspathOrEmpty
    override val classpathWithSources = lhs.classpathWithSources.takeIf { it.isNotEmpty() } ?: rhs.classpathWithSources
}
