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

    fun buildConfigurationContainsError() : Pair<Boolean, String>{
        if (filesWithErrorsTAPI.isEmpty()){
            return Pair(false, String())
        }
        val errorMessage = "Files with errors: $filesWithErrorsTAPI, \n " +
            "Fix errors, save these files and make some changes in this file to get right diagnostic!"
        return Pair(true, errorMessage)
    }


    fun updateBuildEnv(uri: URI){
        updateBuildEnv(uri.toPath())
    }
    fun updateBuildEnv(pathToFile: Path) {
        val classpath = invoke(pathToFile)
        if (classpath.isEmpty()) {
            filesWithErrorsTAPI.add(pathToFile)
            return
        }
        filesWithErrorsTAPI.remove(pathToFile)
        val buildEnv = CompilationEnvironment(emptySet(), classpath)
        buildEnvByFile[pathToFile.toString()] = buildEnv
    }

    fun isBuildScriptWithDynamicClasspath(file: Path): Boolean =
        file.fileName.toString().let { it == "build.gradle.kts" }

    fun isBuildScript(file: Path): Boolean = file.fileName.toString().endsWith(".gradle.kts")

    private fun invoke(pathToFile: Path): Set<Path> {
        val projectDir = pathToFile.parent.toFile()
        val connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()

        return try {
            val model = connection.getModel(KotlinDslScriptsModel::class.java)
            val scriptModels = model.scriptModels

            val neededModel = scriptModels[pathToFile.toFile()]
            val classpath = neededModel?.classPath
            LOG.info { "for $pathToFile tooling API was successful" }
            classpath?.map { it.toPath() }?.let { HashSet(it) } ?: emptySet()
        } catch (e: Exception) {
            val stackTrace = e.stackTraceToString()
            LOG.info { "for $pathToFile tooling API was failed: " }
            emptySet()
        } finally {
            connection.close()
        }
    }


}
