package org.javacs.kt.classpath

import org.javacs.kt.LOG
import java.nio.file.Path

/** A source for creating class paths */
interface ClassPathResolver {
    val resolverType: String
    val classpath: Set<Path> // may throw exceptions
    val classpathOrEmpty: Set<Path> // does not throw exceptions
        get() = try {
            classpath
        } catch (e: Exception) {
            LOG.warn("Could not resolve classpath using {}: {}", resolverType, e.message)
            emptySet<Path>()
        }

    companion object {
        /** A default empty classpath implementation */
        val empty = object : ClassPathResolver {
            override val resolverType = "[]"
            override val classpath = emptySet<Path>()
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
}

internal class FirstNonEmptyClassPathResolver(val lhs: ClassPathResolver, val rhs: ClassPathResolver) : ClassPathResolver {
    override val resolverType: String get() = "(${lhs.resolverType} or ${rhs.resolverType})"
    override val classpath get() = lhs.classpath.takeIf { it.isNotEmpty() } ?: rhs.classpath
    override val classpathOrEmpty get() = lhs.classpathOrEmpty.takeIf { it.isNotEmpty() } ?: rhs.classpathOrEmpty
}
