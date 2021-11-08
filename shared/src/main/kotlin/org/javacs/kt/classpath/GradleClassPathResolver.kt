package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.javacs.kt.util.execAndReadStdoutAndStderr
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.isOSWindows
import org.javacs.kt.util.findCommandOnPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

internal class GradleClassPathResolver(private val path: Path, private val includeKotlinDSL: Boolean): ClassPathResolver {
    override val resolverType: String = "Gradle"
    private val projectDirectory: Path get() = path.parent
    override val classpath: Set<ClassPathEntry> get() {
        val scripts = listOf("projectClassPathFinder.gradle")
        val tasks = listOf("kotlinLSPProjectDeps")

        return readDependenciesViaGradleCLI(projectDirectory, scripts, tasks)
            .apply { if (isNotEmpty()) LOG.info("Successfully resolved dependencies for '${projectDirectory.fileName}' using Gradle") }
    }
    override val buildScriptClasspath: Set<Path> get() {
        return if (includeKotlinDSL) {
            val scripts = listOf("kotlinDSLClassPathFinder.gradle")
            val tasks = listOf("kotlinLSPKotlinDSLDeps")

            return readDependenciesViaGradleCLI(projectDirectory, scripts, tasks)
                .map { it.compiledJar }
                .toSet()
                .apply { if (isNotEmpty()) LOG.info("Successfully resolved build script dependencies for '${projectDirectory.fileName}' using Gradle") }
        } else {
            emptySet()
        }
    }

    companion object {
        /** Create a Gradle resolver if a file is a pom. */
        fun maybeCreate(file: Path): GradleClassPathResolver? =
            file.takeIf { file.endsWith("build.gradle") || file.endsWith("build.gradle.kts") }
                ?.let { GradleClassPathResolver(it, includeKotlinDSL = file.toString().endsWith(".kts")) }
    }
}

private fun gradleScriptToTempFile(scriptName: String, deleteOnExit: Boolean = false): File {
    val config = File.createTempFile("classpath", ".gradle")
    if (deleteOnExit) {
        config.deleteOnExit()
    }

    LOG.debug("Creating temporary gradle file {}", config.absolutePath)

    config.bufferedWriter().use { configWriter ->
        ClassLoader.getSystemResourceAsStream(scriptName)?.bufferedReader()?.use { configReader ->
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

private fun readDependenciesViaGradleCLI(projectDirectory: Path, gradleScripts: List<String>, gradleTasks: List<String>): Set<ClassPathEntry> {
    LOG.info("Resolving dependencies for '{}' through Gradle's CLI using tasks {}...", projectDirectory.fileName, gradleTasks)

    val tmpScripts = gradleScripts.map { gradleScriptToTempFile(it, deleteOnExit = false).toPath().toAbsolutePath() }
    val gradle = getGradleCommand(projectDirectory)

    val command = "$gradle ${tmpScripts.joinToString(" ") { "-I $it" }} ${gradleTasks.joinToString(" ")} --console=plain"
    val dependencies = findGradleCLIDependencies(command, projectDirectory)
        .also { classPath -> LOG.debug("Classpath for task {}", classPath.map { it.compiledJar }) }
        .filter {
            val compiledJar = it.compiledJar
            compiledJar.toString().lowercase().endsWith(".jar") || Files.isDirectory(compiledJar)
        } // Some Gradle plugins seem to cause this to output POMs, therefore filter JARs
        .toSet()

    tmpScripts.forEach(Files::delete)
    return dependencies
}

private fun findGradleCLIDependencies(command: String, projectDirectory: Path): Set<ClassPathEntry> {
    val (result, errors) = execAndReadStdoutAndStderr(command, projectDirectory)
    LOG.debug(result)
    if ("FAILURE: Build failed" in errors) {
        LOG.warn("Gradle task failed: {}", errors.lines().joinToString("\n"))
    }
    return parseGradleCLIDependencies(result)
}

private val artifactPattern by lazy { "(?m)^kotlin-lsp-gradle (.*?([^\\\\/]+)\\.jar)$".toRegex() }
private val sourcePattern by lazy { "(?m)^kotlin-lsp-source (.*?([^\\\\/]+)-sources\\.jar)$".toRegex() }

private fun parseGradleCLIDependencies(output: String): Set<ClassPathEntry> {
    LOG.debug(output)

    val sources = sourcePattern
        .findAll(output)
        .associate { it.groupValues[2] to Paths.get(it.groupValues[1]) }

    return artifactPattern
        .findAll(output)
        .map {
            val filePath = it.groupValues[1]
            val jarName = it.groupValues[2]
            ClassPathEntry(
                compiledJar = Paths.get(filePath),
                sourceJar = sources[jarName]
            )
        }
        .toSet()
}
