package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.javacs.kt.util.firstNonNull
import org.javacs.kt.util.tryResolving
import org.javacs.kt.util.execAndReadStdout
import org.javacs.kt.util.KotlinLSException
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

internal class GradleClassPathResolver(private val path: Path) : ClassPathResolver {
    override val resolverType: String = "Gradle"
    override val classpath: Set<Path> get() {
        val projectDirectory = path.getParent()
        return readDependenciesViaGradleCLI(projectDirectory)
            .orEmpty()
            .apply { if (isNotEmpty()) LOG.info("Successfully resolved dependencies for '${projectDirectory.fileName}' using Gradle") }
    }

    companion object {
        /** Create a Gradle resolver if a file is a pom. */
        fun maybeCreate(file: Path): GradleClassPathResolver? =
            file.takeIf { file.endsWith("build.gradle") || file.endsWith("build.gradle.kts") }
                ?.let { GradleClassPathResolver(it) }
    }
}

private fun createTemporaryGradleFile(deleteOnExit: Boolean = false): File {
    val config = File.createTempFile("classpath", ".gradle")
    if (deleteOnExit) {
        config.deleteOnExit()
    }

    LOG.debug("Creating temporary gradle file {}", config.absolutePath)

    config.bufferedWriter().use { configWriter ->
        ClassLoader.getSystemResourceAsStream("classpathFinder.gradle").bufferedReader().use { configReader ->
            configReader.copyTo(configWriter)
        }
    }

    return config
}

private fun getGradleCommand(workspace: Path): Path {
    val wrapperName = if (isOSWindows()) "gradlew.bat" else "gradlew"
    val wrapper = workspace.resolve(wrapperName).toAbsolutePath()
    if (Files.exists(wrapper)) {
        return wrapper
    } else {
        return workspace.parent?.let(::getGradleCommand)
            ?: findCommandOnPath("gradle")
            ?: throw KotlinLSException("Could not find 'gradle' on PATH")
    }
}

private fun readDependenciesViaGradleCLI(projectDirectory: Path): Set<Path>? {
    LOG.info("Resolving dependencies for '{}' through Gradle's CLI...", projectDirectory.fileName)
    val config = createTemporaryGradleFile(deleteOnExit = false)
    val gradle = getGradleCommand(projectDirectory)
    val cmd = "$gradle -I ${config.absolutePath} kotlinLSPDeps --console=plain"
    LOG.debug("  -- executing {}", cmd)
    val dependencies = findGradleCLIDependencies(cmd, projectDirectory)
    config.delete()
    return dependencies
}

private fun findGradleCLIDependencies(command: String, projectDirectory: Path): Set<Path>? {
    val result = execAndReadStdout(command, projectDirectory)
    LOG.debug(result)
    return parseGradleCLIDependencies(result)
}

private val artifactPattern by lazy { "kotlin-lsp-gradle (.+)(\r?\n)".toRegex() }

private fun parseGradleCLIDependencies(output: String): Set<Path>? {
    val artifacts = artifactPattern.findAll(output)
        .mapNotNull { FileSystems.getDefault().getPath(it.groups[1]?.value) }
        .filterNotNull()
    return artifacts.toSet()
}
