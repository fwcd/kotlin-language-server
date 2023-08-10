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
//        LOG.warn { "workspaceRoots : $workspaceRoots" }
        for (root in workspaceRoots) {
            val (success, mapWithSources) = invokeTAPI(root.toFile())
            LOG.info { "[success=$success] TAPI invoking for $root" }
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
        for (root in workspaceRoots) {
            val (success, mapWithSources) = invokeTAPI(root.toFile())
            for ((_, value) in mapWithSources){
                classpath += value.classPath.map { it.toPath() }.toSet()
            }
        }
        return classpath
    }

    fun invokeTAPI(pathToDirs: File): Pair<Boolean, Map<File, KotlinDslScriptModel>> {
        GradleConnector.newConnector().forProjectDirectory(pathToDirs).connect().use {
            return try {
                val model = it.getModel(KotlinDslScriptsModel::class.java)
                return Pair(true,model.scriptModels)
            } catch (e: Exception) {
                val stackTrace = e.stackTraceToString()
//                LOG.info { "for $pathToDirs tooling API was failed: " }
                return Pair(false, emptyMap())
            }
        }
    }

}
