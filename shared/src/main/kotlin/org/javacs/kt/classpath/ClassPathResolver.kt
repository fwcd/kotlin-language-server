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
operator fun ClassPathResolver.plus(other: ClassPathResolver): ClassPathResolver =
    object : ClassPathResolver {
        override val resolverType: String get() = "${this@plus.resolverType} + ${other.resolverType}"
        override val classpath get() = this@plus.classpath + other.classpath
        override val classpathOrEmpty get() = this@plus.classpathOrEmpty + other.classpathOrEmpty
    }

/** Uses the left-hand classpath if not empty, otherwise uses the right. */
fun ClassPathResolver.or(other: ClassPathResolver): ClassPathResolver =
    object : ClassPathResolver {
        override val resolverType: String get() = "${this@or.resolverType} or ${other.resolverType}"
        override val classpath get() = this@or.classpath.takeIf { it.isNotEmpty() } ?: other.classpath
        override val classpathOrEmpty get() = this@or.classpathOrEmpty.takeIf { it.isNotEmpty() } ?: other.classpathOrEmpty
    }
