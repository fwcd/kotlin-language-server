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
import org.gradle.tooling.GradleConnector

fun readBuildGradle(buildFile: Path): Set<Path> {
    val projectDirectory = buildFile.getParent()
    val connection = GradleConnector.newConnector()
            .forProjectDirectory(projectDirectory.toFile())
            .connect()

    // The first successful dependency resolver is used
    // (evaluating them from top to bottom)
    var dependencies = firstNonNull<Set<Path>>(
        { tryResolving("dependencies using Gradle dependencies CLI") { readDependenciesViaGradleCLI(projectDirectory) } }
    ).orEmpty()

    if (dependencies.isEmpty()) {
        LOG.warning("Could not resolve Gradle dependencies using any resolution strategy!")
    }

    connection.close()
    return dependencies
}

private fun createTemporaryGradleFile(): File {
    val config = File.createTempFile("classpath", ".gradle")

    LOG.info("Creating temporary gradle file ${config.absolutePath}")

    config.bufferedWriter().use { configWriter ->
        ClassLoader.getSystemResourceAsStream("classpathFinder.gradle").bufferedReader().use { configReader ->
            configReader.copyTo(configWriter)
        }
    }

    return config
}

private fun getGradleCommand(workspace: Path): Path {
    val wrapper = workspace.resolve("gradlew").toAbsolutePath()
    if (Files.exists(wrapper)) {
        return wrapper
    } else {
        return findCommandOnPath("gradle") ?: throw KotlinLSException("Could not find 'gradle' on PATH")
    }
}

private fun readDependenciesViaGradleCLI(projectDirectory: Path): Set<Path>? {
    LOG.fine("Attempting dependency resolution through CLI...")
    val config = createTemporaryGradleFile()
    val gradle = getGradleCommand(projectDirectory)
    val cmd = "$gradle -I ${config.absolutePath} kotlinLSPDeps --console=plain"
    LOG.fine("  -- executing $cmd")
    val dependencies = findGradleCLIDependencies(cmd, projectDirectory)
    return dependencies
}

private fun findGradleCLIDependencies(command: String, projectDirectory: Path): Set<Path>? {
    val result = execAndReadStdout(command, projectDirectory)
    LOG.fine(result)
    return parseGradleCLIDependencies(result)
}

private val artifactPattern by lazy { "kotlin-lsp-gradle (.+)\n".toRegex() }

private fun parseGradleCLIDependencies(output: String): Set<Path>? {
    val artifacts = artifactPattern.findAll(output)
        .mapNotNull { FileSystems.getDefault().getPath(it.groups[1]?.value) }
        .filterNotNull()
    return artifacts.toSet()
}
