package org.javacs.kt.compiler

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.javacs.kt.LOG
import java.io.File
import java.nio.file.Files

import java.nio.file.Path

object BuildFileManager {
    val buildEnvByFile: MutableMap<Path, CompilationEnvironment> = mutableMapOf()

    var workspaceRoots = emptySet<Path>()

    private var errorDuringTAPICall = false

    private val errorMessage = "Files with errors:, \n " +
        "Fix errors, save these files and make some changes in this file to get right diagnostic!"

    fun buildConfigurationContainsError(): Boolean {
        return errorDuringTAPICall
    }


    fun updateBuildEnv() {

        val rootWorkspaces = workspaceRoots.filter { workspaceIsRoot(it) }

        for (workspace in rootWorkspaces) {
            val (success, mapWithSources) = invokeTAPI(workspace.toFile())
            LOG.info { "[success=$success] TAPI invoking for $workspace" }
            if (!success){
                errorDuringTAPICall = true
                return
            }

            for ((file, model) in mapWithSources) {
                val classpath = model.classPath.map { it.toPath() }.let { HashSet(it) }
                buildEnvByFile[file.toPath()] = CompilationEnvironment(emptySet(), classpath)
            }
        }
        errorDuringTAPICall = false
    }

    fun isBuildScriptWithDynamicClasspath(file: Path): Boolean =
        file.fileName.toString().let { it == "build.gradle.kts" }

    fun isBuildScript(file: Path): Boolean = file.fileName.toString().endsWith(".gradle.kts")


    fun getCommonBuildClasspath(): Set<Path> {
        if (errorDuringTAPICall) {
            val tempDir: Path = Files.createTempDirectory("temp-dir").toAbsolutePath()
            val tempFile = tempDir.resolve("settings.gradle.kts")
            Files.write(tempFile, "".toByteArray())

            val (_, models) = invokeTAPI(tempDir.toFile())
            val classpath = models.entries.first().value.classPath
            return classpath.map { it.toPath() }.toSet()
        }
        val classpath = mutableSetOf<Path>()
        val rootWorkspaces = workspaceRoots.filter { workspaceIsRoot(it) }
        for (root in rootWorkspaces) {
            val (_, mapWithSources) = invokeTAPI(root.toFile())
            for ((_, value) in mapWithSources){
                classpath += value.classPath.map { it.toPath() }.toSet()
            }
        }
        return classpath
    }

    private fun workspaceIsRoot(path: Path) : Boolean{
        val directory = path.toFile()
        LOG.info {  "$path and files:${directory.listFiles()}" }
        return directory.listFiles().any { it.name == "settings.gradle.kts" }
    }

    fun invokeTAPI(pathToDirs: File): Pair<Boolean, Map<File, KotlinDslScriptModel>> {
        GradleConnector.newConnector().forProjectDirectory(pathToDirs).connect().use {
            return try {
                // use it.action(BuildAction) to traverse the build tree (root build + included builds in a composite build)
                val model = it.getModel(KotlinDslScriptsModel::class.java)
                Pair(true,model.scriptModels)
            } catch (e: Exception) {
//                val stackTrace = e.stackTraceToString()
//                LOG.info { "for $pathToDirs tooling API was failed: " }
                Pair(false, emptyMap())
            }
        }
    }

}
