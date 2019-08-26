package org.javacs.kt.classpath

import java.nio.file.Path
import java.nio.file.Files
import java.util.stream.Collectors
import org.javacs.kt.util.findCommandOnPath

/** Tries to find the Kotlin command line compiler's libraries. */
internal class KotlinCliClassPathResolver(private val libDir: Path) : ClassPathResolver {
    override val resolverType: String = "Kotlinc"
    override val classpath: Set<Path>
        get() = Files.list(libDir).collect(Collectors.toSet())

    companion object {
        /**
         * Creates a resolver from the global kotlinc's libraries
         * if they can be found.
         */
        fun global(): ClassPathResolver =
            findCommandOnPath("kotlinc")
                ?.toRealPath()
                ?.parent
                ?.resolve("lib")
                ?.resolve("libexec")
                ?.takeIf { Files.exists(it) }
                ?.let(::KotlinCliClassPathResolver)
                ?: ClassPathResolver.empty
    }
}
