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

    override val classpath: Set<Path> get() {
        val projectDirectory = path.getParent()

        // The first successful dependency resolver is used
        // (evaluating them from top to bottom)
        var dependencies = firstNonNull<Set<Path>>(
            { tryResolving("dependencies using Gradle dependencies CLI") { readDependenciesViaGradleCLI(projectDirectory) } }
        ).orEmpty()

        if (dependencies.isEmpty()) {
            LOG.warn("Could not resolve Gradle dependencies using any resolution strategy!")
        }

        return dependencies
    }

    companion object {

        /** Create a maven resolver if a file is a pom */
        fun maybeCreate(file: Path): GradleClassPathResolver? =
            file.takeIf { file.endsWith("build.gradle") || file.endsWith("build.gradle.kts") }
                ?.let { GradleClassPathResolver(it) }
    }
}

private fun createTemporaryGradleFile(): File {
    val config = File.createTempFile("classpath", ".gradle")
    config.deleteOnExit()

    LOG.info("Creating temporary gradle file {}", config.absolutePath)

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
        return findCommandOnPath("gradle") ?: throw KotlinLSException("Could not find 'gradle' on PATH")
    }
}

private fun readDependenciesViaGradleCLI(projectDirectory: Path): Set<Path>? {
    LOG.info("Resolving dependencies for {} through Gradle's CLI...", projectDirectory.fileName)
    val config = createTemporaryGradleFile()
    val gradle = getGradleCommand(projectDirectory)
    val cmd = "$gradle -I ${config.absolutePath} kotlinLSPDeps --console=plain"
    LOG.debug("  -- executing {}", cmd)
    val dependencies = findGradleCLIDependencies(cmd, projectDirectory)
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
