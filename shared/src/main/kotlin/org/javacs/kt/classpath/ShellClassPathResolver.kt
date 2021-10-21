package org.javacs.kt.classpath

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.javacs.kt.util.userHome
import org.javacs.kt.util.isOSWindows
import org.javacs.kt.util.execAndReadStdoutAndStderr
import org.javacs.kt.LOG

/** Executes a shell script to determine the classpath */
internal class ShellClassPathResolver(
    private val script: Path,
    private val workingDir: Path? = null
) : ClassPathResolver {
    override val resolverType: String = "Shell"
    override val classpath: Set<ClassPathEntry> get() {
        val workingDirectory = workingDir ?: script.toAbsolutePath().parent
        val cmd = script.toString()
        LOG.info("Run {} in {}", cmd, workingDirectory)
        val (result, errors) = execAndReadStdoutAndStderr(cmd, workingDirectory)
        if (errors.isNotBlank()) {
            LOG.warn("ShellClassPathResolver {} stderr: {}", cmd, errors.lines().joinToString("\n"))
        }

        return result
            .split(separatorChar)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { ClassPathEntry(Paths.get(it), null) }
            .toSet()
    }

    companion object {
        private val execExtension = if (isOSWindows()) "bat" else "sh"
        private val separatorChar = if (isOSWindows()) ';' else ':' 

        /** Create a shell resolver if a file is a pom. */
        fun maybeCreate(file: Path): ShellClassPathResolver? =
            file.takeIf { it.endsWith("kotlinLspClasspath." + execExtension) }?.let { ShellClassPathResolver(it) }

        /** The root directory for config files. */
        private val globalConfigRoot: Path =
            System.getenv("XDG_CONFIG_HOME")?.let { Paths.get(it) } ?: userHome.resolve(".config")

        /** Returns the ShellClassPathResolver for the global home directory shell script. */
        fun global(workingDir: Path?): ClassPathResolver =
            globalConfigRoot.resolve("KotlinLanguageServer").resolve("classpath." + execExtension)
                .takeIf { Files.exists(it) }
                ?.let { ShellClassPathResolver(it, workingDir) }
                ?: ClassPathResolver.empty
    }
}
