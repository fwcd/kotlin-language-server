package org.javacs.kt.classpath

import org.javacs.kt.LOG
import java.nio.file.Path
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
    var dependencies: Set<Path> = emptySet()

    try {
        dependencies = readDependenciesViaEclipseProject(connection)
    } catch (e: BuildException) {
        try {
            dependencies = readDependenciesViaKotlinDSL(connection)
        } catch (f: BuildException) {
            try {
                dependencies = readDependenciesViaIdeaProject(connection)
            } catch (g: BuildException) {
                LOG.warning("BuildExceptions while collecting Gradle dependencies: ${e.message} and ${f.message} and ${g.message}")
            }
        }
    }

    connection.close()
    return dependencies
}

private fun readDependenciesViaEclipseProject(connection: ProjectConnection): Set<Path> {
    val dependencies = mutableSetOf<Path>()
    val project: EclipseProject = connection.getModel(EclipseProject::class.java)

    for (dependency in project.classpath) {
        dependencies.add(dependency.file.toPath())
    }

    return dependencies;
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

    return dependencies;
}

private fun readDependenciesViaKotlinDSL(connection: ProjectConnection): Set<Path> {
    val project: KotlinBuildScriptModel = connection.getModel(KotlinBuildScriptModel::class.java)
    return project.classPath.map { it.toPath() }.toSet()
}
