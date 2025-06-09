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
    override val buildScriptClasspath: Set<Path>
        get() {
        return if (includeKotlinDSL) {
            val scripts = listOf("kotlinDSLClassPathFinder.gradle")
            val tasks = listOf("kotlinLSPKotlinDSLDeps")

            return readDependenciesViaGradleCLI(projectDirectory, scripts, tasks)
                .apply { if (isNotEmpty()) LOG.info("Successfully resolved build script dependencies for '${projectDirectory.fileName}' using Gradle") }
                .map {
                    it.compiledJar
                }.toSet()
        } else {
            emptySet()
        }
    }

    override val currentBuildFileVersion: Long get() = path.toFile().lastModified()

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
        GradleClassPathResolver::class.java.getResourceAsStream("/$scriptName").bufferedReader().use { configReader ->
            configReader.copyTo(configWriter)
        }
    }

    return config
}

private fun getGradleCommand(workspace: Path): Path {
    val wrapperName = if (isOSWindows()) "gradlew.bat" else "gradlew"
    val wrapper = workspace.resolve(wrapperName).toAbsolutePath()
    if (Files.isExecutable(wrapper)) {
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

    val command = listOf(gradle.toString()) + tmpScripts.flatMap { listOf("-I", it.toString()) } + gradleTasks + listOf("--console=plain")
    val dependencies = findGradleCLIDependencies(command, projectDirectory)

    tmpScripts.forEach(Files::delete)
    return dependencies
}

private fun findGradleCLIDependencies(command: List<String>, projectDirectory: Path): Set<ClassPathEntry> {
    val (result, errors) = execAndReadStdoutAndStderr(command, projectDirectory)
    if ("FAILURE: Build failed" in errors) {
        LOG.warn("Gradle task failed: {}", errors)
    } else {
        for (error in errors.lines()) {
            if ("ERROR: " in error) {
                LOG.warn("Gradle error: {}", error)
            }
        }
    }
    return parseGradleCLIDependencies(result)
}

private val artifactPattern by lazy { "kotlin-lsp-gradle path:(.+) source:(.+)".toRegex() }
private val gradleErrorWherePattern by lazy { "\\*\\s+Where:[\r\n]+(\\S\\.*)".toRegex() }

private fun parseGradleCLIDependencies(output: String): Set<ClassPathEntry> {
    LOG.debug(output)
    val artifacts = artifactPattern.findAll(output)
        .map {
            val path = it.groups[1]?.value
            val source = it.groups[2]?.value
            val jarPath = if (path == "null" || path == null) null else Path.of(path)
            val sourceJarPath = if (source == "null" || source == null) null else Path.of(source)
            if (jarPath != null && (path!!.lowercase().endsWith(".jar") || Files.isDirectory(jarPath))) {
                LOG.debug { "Adding path:$jarPath source: $sourceJarPath to classpath" }
                return@map ClassPathEntry(jarPath, sourceJarPath)
            } else return@map null
        }.filterNotNull()

    return artifacts.toSet()
}
