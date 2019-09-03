package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.javacs.kt.util.firstNonNull
import org.javacs.kt.util.tryResolving
import org.javacs.kt.util.execAndReadStdout
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.isOSWindows
import org.javacs.kt.util.findCommandOnPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class GradleClassPathResolver(private val path: Path, private val includeGradleLibs: Boolean): ClassPathResolver {
    override val resolverType: String = "Gradle"
    override val classpath: Set<Path> get() {
        val projectDirectory = path.getParent()
        val tasks = listOf("kotlinLSPProjectDeps") + (if (includeGradleLibs) listOf("kotlinLSPGradleDeps") else emptySet())
        return readDependenciesViaGradleCLI(projectDirectory, tasks)
            .apply { if (isNotEmpty()) LOG.info("Successfully resolved dependencies for '${projectDirectory.fileName}' using Gradle") }
    }

    companion object {
        /** Create a Gradle resolver if a file is a pom. */
        fun maybeCreate(file: Path): GradleClassPathResolver? =
            file.takeIf { file.endsWith("build.gradle") || file.endsWith("build.gradle.kts") }
                ?.let { GradleClassPathResolver(it, file.toString().endsWith(".kts")) }
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

private fun readDependenciesViaGradleCLI(projectDirectory: Path, gradleTasks: List<String>): Set<Path> {
    LOG.info("Resolving dependencies for '{}' through Gradle's CLI using tasks {}...", projectDirectory.fileName, gradleTasks)
    val tmpFile = createTemporaryGradleFile(deleteOnExit = false).toPath()
    val gradle = getGradleCommand(projectDirectory)
    val dependencies = gradleTasks.flatMap { queryGradleCLIDependencies(gradle, tmpFile, it, projectDirectory).orEmpty() }.toSet()
    Files.delete(tmpFile)
    return dependencies
}

private fun queryGradleCLIDependencies(gradle: Path, tmpFile: Path, task: String, projectDirectory: Path): Set<Path>? =
    findGradleCLIDependencies("$gradle -I ${tmpFile.toAbsolutePath()} $task --console=plain", projectDirectory)
        ?.also { LOG.debug("Classpath for task {}", it) }

private fun findGradleCLIDependencies(command: String, projectDirectory: Path): Set<Path>? {
    val result = execAndReadStdout(command, projectDirectory)
    LOG.debug(result)
    return parseGradleCLIDependencies(result)
}

private val artifactPattern by lazy { "kotlin-lsp-gradle (.+)(?:\r?\n)".toRegex() }

private fun parseGradleCLIDependencies(output: String): Set<Path>? {
    LOG.debug(output)
    val artifacts = artifactPattern.findAll(output)
        .mapNotNull { Paths.get(it.groups[1]?.value) }
        .filterNotNull()
    return artifacts.toSet()
}
