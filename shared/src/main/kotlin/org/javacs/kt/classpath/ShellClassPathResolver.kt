package org.javacs.kt.classpath

import java.io.File
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
    override val classpath: Set<ClassPathEntry> get() {
        val workingDirectory = workingDir?.toFile() ?: script.toAbsolutePath().parent.toFile()
        val cmd = script.toString()
        LOG.info("Run {} in {}", cmd, workingDirectory)
        val process = ProcessBuilder(cmd).directory(workingDirectory).start()

        return process.inputStream.bufferedReader().readText()
            .split(File.pathSeparator)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ClassPathEntry(Paths.get(it), null) }
            .toSet()
    }

    companion object {
        private val scriptExtensions = listOf("sh", "bat", "cmd")

        /** Create a shell resolver if a file is a pom. */
        fun maybeCreate(file: Path): ShellClassPathResolver? =
            file.takeIf { scriptExtensions.any { file.endsWith("kotlinLspClasspath.$it") } }
                ?.let { ShellClassPathResolver(it) }

        /** The root directory for config files. */
        private val globalConfigRoot: Path =
            System.getenv("XDG_CONFIG_HOME")?.let { Paths.get(it) } ?: userHome.resolve(".config")

        /** Returns the ShellClassPathResolver for the global home directory shell script. */
        fun global(workingDir: Path?): ClassPathResolver =
            globalConfigRoot.resolve("KotlinLanguageServer")
                ?.let { root ->
                    scriptExtensions
                        .map { root.resolve("classpath.$it") }
                        .firstOrNull { Files.exists(it) }
                }
                ?.let { ShellClassPathResolver(it, workingDir) }
                ?: ClassPathResolver.empty
    }
}
