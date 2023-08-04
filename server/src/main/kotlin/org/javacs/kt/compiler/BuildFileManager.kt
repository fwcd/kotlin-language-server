package org.javacs.kt.compiler

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.javacs.kt.LOG
import java.net.URI

import java.nio.file.Path
import kotlin.io.path.toPath

object BuildFileManager {
    val buildEnvByFile: MutableMap<String, CompilationEnvironment> = mutableMapOf()

    private val filesWithErrorsTAPI: MutableSet<Path> = mutableSetOf()

    fun checkErrorFile(uri: URI): Boolean = filesWithErrorsTAPI.contains(uri.toPath())


    fun updateBuildEnv(uri: URI) {
        val pathToFile = uri.toPath()
        // for usual build files we need to get classpath only at first time
        if (!isBuildScriptWithDynamicClasspath(pathToFile) && buildEnvByFile.contains(pathToFile.toString())) {
            return
        }
        val classpath = invoke(pathToFile)
        if (classpath.isEmpty()) {
            filesWithErrorsTAPI.add(pathToFile)
            LOG.info { "for $uri tooling API was unsuccessful" }
            return
        }
        filesWithErrorsTAPI.remove(pathToFile)
        val buildEnv = CompilationEnvironment(emptySet(), classpath)
        buildEnvByFile[pathToFile.toString()] = buildEnv
    }

    fun isBuildScriptWithDynamicClasspath(file: Path): Boolean =
        file.fileName.toString().let { it == "build.gradle.kts" }

    fun isBuildScript(file: Path): Boolean = file.fileName.toString().endsWith(".gradle.kts")

    operator fun invoke(pathToFile: Path): Set<Path> {
        val projectDir = pathToFile.parent.toFile()
        val connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()

        return try {
            val model = connection.getModel(KotlinDslScriptsModel::class.java)
            val scriptModels = model.scriptModels

            val firstScriptModel = scriptModels[pathToFile.toFile()]
            val classpath = firstScriptModel?.classPath
            classpath?.map { it.toPath() }?.let { HashSet(it) } ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        } finally {
            connection.close()
        }
    }


}
