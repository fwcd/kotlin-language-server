package org.javacs.kt.compiler

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

import org.javacs.kt.LOG
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object BuildFileManager {
    val buildEnvByFile: MutableMap<Path, CompilationEnvironment> = mutableMapOf()

    private var workspaceRoots = emptySet<Path>()

    private var TAPICallFailed = false

    private val error = Diagnostic(Range(Position(0, 0), Position(0, 0)), String())

    fun getError(): Diagnostic = error

    fun setWorkspaceRoots(wr: Set<Path>) = run { workspaceRoots = wr }

    fun buildConfContainsError(): Boolean = TAPICallFailed

    fun updateBuildEnv() {
        val rootWorkspaces = workspaceRoots.filter { workspaceIsRoot(it) }

        for (workspace in rootWorkspaces) {
            val (success, scriptModelByFile) = invokeTAPI(workspace.toFile())
            LOG.info { "[success=$success] TAPI invoking for $workspace" }
            if (!success) {
                TAPICallFailed = true
                return
            }

            for ((file, model) in scriptModelByFile) {
                val classpath = model.classPath.map { it.toPath() }.let { HashSet(it) }
                buildEnvByFile[file.toPath()] = CompilationEnvironment(emptySet(), classpath)
            }
        }
        TAPICallFailed = false
    }

    fun isBuildScriptWithDynamicClasspath(file: Path): Boolean =
        file.fileName.toString().let { it == "build.gradle.kts" }

    fun isBuildScript(file: Path): Boolean = file.fileName.toString().endsWith(".gradle.kts")

    fun getCommonBuildClasspath(): Set<Path> {
        if (TAPICallFailed) {
            // KLS takes build classpath from temporary settings build file to provide correct compilation on initial stage
            val tempDir: Path = Files.createTempDirectory("temp-dir").toAbsolutePath()
            val settingsBuildFile = tempDir.resolve("settings.gradle.kts")
            Files.write(settingsBuildFile, "".toByteArray())

            val models = invokeTAPI(tempDir.toFile()).second
            val classpath = models.entries.first().value.classPath
            return classpath.map { it.toPath() }.toSet()
        }
        val commonClasspath = mutableSetOf<Path>()
        val rootWorkspaces = workspaceRoots.filter { workspaceIsRoot(it) }
        for (root in rootWorkspaces) {
            val (_, mapWithSources) = invokeTAPI(root.toFile())
            for (scriptModel in mapWithSources.values) {
                commonClasspath += scriptModel.classPath.map { it.toPath() }.toSet()
            }
        }
        return commonClasspath
    }

    private fun workspaceIsRoot(path: Path): Boolean {
        val directory = path.toFile()
        return directory.listFiles().any { it.name == "settings.gradle.kts" }
    }

    private fun invokeTAPI(pathToDirs: File): Pair<Boolean, Map<File, KotlinDslScriptModel>> {
        GradleConnector.newConnector().forProjectDirectory(pathToDirs).connect().use {
            return try {
                // use it.action(BuildAction) to traverse the build tree (root build + included builds in a composite build)
                val model = it.getModel(KotlinDslScriptsModel::class.java)
                Pair(true, model.scriptModels)
            } catch (e: Exception) {
                error.message = "Fix errors and save the file \n ${(e.cause?.message ?: e.message)}"
//                val stackTrace = e.stackTraceToString()
                Pair(false, emptyMap())
            }
        }
    }

}
