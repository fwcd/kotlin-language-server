package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.javacs.kt.util.optionalOr
import java.io.File
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
    val projectDirectory = buildFile.getParent().toFile()
    val connection = GradleConnector.newConnector()
            .forProjectDirectory(projectDirectory)
            .connect()
    var dependencies: Set<Path> = optionalOr<Set<Path>>(
        { tryResolvingDependencies("Gradle task") { readDependenciesViaTask(projectDirectory.toPath()) } },
        { tryResolvingDependencies("Eclipse project model") { readDependenciesViaEclipseProject(connection) } },
        { tryResolvingDependencies("Kotlin DSL model") { readDependenciesViaKotlinDSL(connection) } },
        { tryResolvingDependencies("IDEA model") { readDependenciesViaIdeaProject(connection) } }
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

    LOG.info("Reading classpathFinder.gradle")
    ClassLoader.getSystemResourceAsStream("classpathFinder.gradle")
            .bufferedReader()
            .copyTo(config.bufferedWriter())
    LOG.info("Successfully read classpathFinder.gradle")

    val tempWriter = temp.bufferedWriter()
    tempWriter.write("rootProject { apply from: '${config.absolutePath}'} ")
    tempWriter.close()

    return temp
}

private var cacheGradleCommand: Path? = null

private fun getGradleCommand(workspace: Path): Path {
    if (cacheGradleCommand == null) {
        cacheGradleCommand = workspace.resolve("gradlew").toAbsolutePath()
    }

    return cacheGradleCommand!!
}

private fun readDependenciesViaTask(directory: Path): Set<Path>? {
    val gradle = getGradleCommand(directory)
    if (!gradle.toFile().exists()) return mutableSetOf<Path>()
    val config = createTemporaryGradleFile()

    val gradleCommand = "${gradle} -I ${config.absolutePath} classpath"
    val classpathCommand = Runtime.getRuntime().exec(gradleCommand, null, directory.toFile())

    val stdout = classpathCommand.inputStream
    val reader = stdout.bufferedReader()

    classpathCommand.waitFor()

    val artifact = Pattern.compile("^.+?\\.jar$")
    val dependencies = mutableSetOf<Path>()
    for (dependency in reader.lines()) {
        val line = dependency.toString().trim()

        if (artifact.matcher(line).matches()) {
            dependencies.add(Paths.get(line))
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
