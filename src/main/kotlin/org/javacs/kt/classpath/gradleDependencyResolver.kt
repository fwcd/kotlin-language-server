package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.javacs.kt.util.firstNonNull
import org.javacs.kt.util.execAndReadStdout
import org.javacs.kt.util.KotlinLSException
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import org.gradle.tooling.*
import org.gradle.tooling.model.*
import org.gradle.tooling.model.eclipse.*
import org.gradle.tooling.model.idea.*
import org.gradle.kotlin.dsl.tooling.models.*

fun readBuildGradle(buildFile: Path): Set<Path> {
    val projectDirectory = buildFile.getParent()
    val connection = GradleConnector.newConnector()
            .forProjectDirectory(projectDirectory.toFile())
            .connect()

    // The first successful dependency resolver is used
    // (evaluating them from top to bottom)
    var dependencies = firstNonNull<Set<Path>>(
        { tryResolvingDependencies("Gradle task") { readDependenciesViaTask(projectDirectory) } },
        { tryResolvingDependencies("Eclipse project model") { readDependenciesViaEclipseProject(connection) } },
        { tryResolvingDependencies("Kotlin DSL model") { readDependenciesViaKotlinDSL(connection) } },
        { tryResolvingDependencies("IDEA model") { readDependenciesViaIdeaProject(connection) } },
        { tryResolvingDependencies("Gradle dependencies CLI") { readDependenciesViaGradleCLI(projectDirectory) } }
    ).orEmpty()

    if (dependencies.isEmpty()) {
        LOG.warning("Could not resolve Gradle dependencies using any resolution strategy!")
    }

    connection.close()
    return dependencies
}

private fun tryResolvingDependencies(modelName: String, resolver: () -> Set<Path>?): Set<Path>? {
    try {
        val resolved = resolver()
        if (resolved != null) {
            LOG.info("Successfully resolved dependencies using " + modelName)
            return resolved
        }
    } catch (e: Exception) {}
    return null
}

private fun createTemporaryGradleFile(): File {
    val temp = File.createTempFile("tempGradle", ".config")
    val config = File.createTempFile("classpath", ".gradle")

    config.bufferedWriter().use { configWriter ->
        ClassLoader.getSystemResourceAsStream("classpathFinder.gradle").bufferedReader().use { configReader ->
            configReader.copyTo(configWriter)
        }
    }

    temp.bufferedWriter().use {
        it.write("rootProject { apply from: '${config.absolutePath}'} ")
    }

    return temp
}

private fun getGradleCommand(workspace: Path): Path {
    val wrapper = workspace.resolve("gradlew").toAbsolutePath()
    if (Files.exists(wrapper)) {
        return wrapper
    } else {
        return findCommandOnPath("gradle") ?: throw KotlinLSException("Could not find 'gradle' on PATH")
    }
}

private fun readDependenciesViaTask(directory: Path): Set<Path>? {
    val gradle = getGradleCommand(directory)
    val config = createTemporaryGradleFile()

    val gradleCommand = "$gradle -I ${config.absolutePath} classpath"
    val classpathCommand = Runtime.getRuntime().exec(gradleCommand, null, directory.toFile())
    val stdout = classpathCommand.inputStream
    val artifact = Pattern.compile("^.+?\\.jar$")
    val dependencies = mutableSetOf<Path>()

    classpathCommand.waitFor()

    stdout.bufferedReader().use { reader ->
        for (dependency in reader.lines()) {
            val line = dependency.toString().trim()

            if (artifact.matcher(line).matches()) {
                dependencies.add(Paths.get(line))
            }
        }
    }

    if (dependencies.size > 0) {
        return dependencies
    } else {
        return null
    }
}

private fun readDependenciesViaEclipseProject(connection: ProjectConnection): Set<Path> {
    val dependencies = mutableSetOf<Path>()
    val project: EclipseProject = connection.getModel(EclipseProject::class.java)

    for (dependency in project.classpath) {
        dependencies.add(dependency.file.toPath())
    }

    return dependencies
}

private fun readDependenciesViaIdeaProject(connection: ProjectConnection): Set<Path> {
    val dependencies = mutableSetOf<Path>()
    val project: IdeaProject = connection.getModel(IdeaProject::class.java)

    for (child in project.children) {
        for (dependency in child.dependencies) {
            if (dependency is ExternalDependency) {
                dependencies.add(dependency.file.toPath())
            }
        }
    }

    return dependencies
}

private fun readDependenciesViaKotlinDSL(connection: ProjectConnection): Set<Path> {
    val project: KotlinBuildScriptModel = connection.getModel(KotlinBuildScriptModel::class.java)
    return project.classPath.map { it.toPath() }.toSet()
}

private fun readDependenciesViaGradleCLI(projectDirectory: Path): Set<Path>? {
    LOG.fine("Attempting dependency resolution through CLI...")
    val gradle = getGradleCommand(projectDirectory)
    val classpathCommand = "$gradle dependencies --configuration=compileClasspath --console=plain"
    val testClasspathCommand = "$gradle dependencies --configuration=testCompileClasspath --console=plain"
    val dependencies = findGradleCLIDependencies(classpathCommand, projectDirectory)
    val testDependencies = findGradleCLIDependencies(testClasspathCommand, projectDirectory)

    return dependencies?.union(testDependencies.orEmpty()).orEmpty()
}

private fun findGradleCLIDependencies(command: String, projectDirectory: Path): Set<Path>? {
    return parseGradleCLIDependencies(execAndReadStdout(command, projectDirectory))
}

private val artifactPattern by lazy { "[\\S]+:[\\S]+:[\\S]+( -> )*([\\d.]+)*".toRegex() }
// TODO: Resolve the gradleCaches dynamically instead of hardcoding this path
private val gradleCaches by lazy { gradleHome.resolve("caches").resolve("modules-2").resolve("files-2.1") }
private val jarMatcher by lazy { FileSystems.getDefault().getPathMatcher("glob:**.jar") }

private fun parseGradleCLIDependencies(output: String): Set<Path>? {
    val artifacts = artifactPattern.findAll(output)
        .map { findGradleArtifact(parseArtifact(it.value, it.groups[2]?.value)) }
        .filterNotNull()
    return artifacts.toSet()
}

private fun findGradleArtifact(artifact: Artifact): Path? {
    val jarPath = gradleCaches
            ?.resolve(artifact.group)
            ?.resolve(artifact.artifact)
            ?.resolve(artifact.version)
            ?.let { dependencyFolder ->
                Files.walk(dependencyFolder)
                        .filter { jarMatcher.matches(it) }
                        .findFirst()
            }
            ?.orElse(null)
    return jarPath
}
