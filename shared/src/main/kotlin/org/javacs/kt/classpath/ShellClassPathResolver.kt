package org.javacs.kt.classpath

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.javacs.kt.util.userHome
import org.javacs.kt.LOG

/** Executes a shell script to determine the classpath */
internal class ShellClassPathResolver(
    private val script: Path,
    private val workingDir: Path? = null
) : ClassPathResolver {
    override val resolverType: String = "Shell"
    override val classpath: Set<Path> get() {
        val workingDirectory = workingDir?.toFile() ?: script.toAbsolutePath().parent.toFile()
        val cmd = script.toString()
        LOG.info("Run {} in {}", cmd, workingDirectory)
        val process = Runtime.getRuntime().exec(cmd, null, workingDirectory)

        return process.inputStream.bufferedReader().readText()
            .split(':')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Paths.get(it) }
            .toSet()
    }

    companion object {
        /** Create a shell resolver if a file is a pom. */
        fun maybeCreate(file: Path): ShellClassPathResolver? =
            file.takeIf { it.endsWith("kotlinLspClasspath.sh") }?.let { ShellClassPathResolver(it) }

        /** The root directory for config files. */
        private val globalConfigRoot: Path =
            System.getenv("XDG_CONFIG_HOME")?.let { Paths.get(it) } ?: userHome.resolve(".config")

        /** Returns the ShellClassPathResolver for the global home directory shell script. */
        fun global(workingDir: Path?): ClassPathResolver =
            globalConfigRoot.resolve("KotlinLanguageServer").resolve("classpath.sh")
                .takeIf { Files.exists(it) }
                ?.let { ShellClassPathResolver(it, workingDir) }
                ?: ClassPathResolver.empty
    }
}
