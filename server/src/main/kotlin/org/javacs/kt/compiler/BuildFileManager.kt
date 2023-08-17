package org.javacs.kt.compiler

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.javacs.kt.CompositeFinder
import org.javacs.kt.CompositeModelQueryKotlin

import org.javacs.kt.LOG
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object BuildFileManager {
    var buildEnvByFile: MutableMap<Path, CompilationEnvironment> = mutableMapOf()

    private var workspaceRoots = emptySet<Path>()

    private var TAPICallFailed = false

    private val error = Diagnostic(Range(Position(0, 0), Position(0, 0)), String())

    private var defaultBuildClasspath = emptySet<Path>()

    private var initializedWorkspaces = emptySet<Path>()

    fun getError(): Diagnostic = error

    fun setWorkspaceRoots(wr: Set<Path>) = run { workspaceRoots = wr }

    fun buildConfContainsError(): Boolean = TAPICallFailed

    fun updateBuildEnvironment(pathToFile: Path) {
        val workspace = pathToFile.parent
        LOG.info { "UPDATE build env for $workspace" }
        val workspaceForCall = getWorkspaceForCall(workspace) ?: return

        // this condition means that we have already invoked TAPI for this directory
        // why if I type this if I got exceptions from compiler, but if I don't then all is good
//        if (initializedWorkspaces.contains(workspace)) return
//        initializedWorkspaces += setOf<Path>(workspace)

        makeTapiCall(workspaceForCall.toFile())
    }

    fun updateBuildEnvironments() {
        LOG.info { "UPDATE BUILD ENVIRONMENTS" }
        val workspacesForTAPI = ArrayList<File>()
        for (workspace in workspaceRoots) {
            val workspaceForCall = getWorkspaceForCall(workspace) ?: continue
            workspacesForTAPI.add(workspaceForCall.toFile())
        }

        for (workspace in workspacesForTAPI) {
            makeTapiCall(workspace)
        }
        TAPICallFailed = false
    }

    fun isBuildScriptWithDynamicClasspath(file: Path): Boolean =
        file.fileName.toString().let { it == "build.gradle.kts" }

    fun isBuildScript(file: Path): Boolean = file.fileName.toString().endsWith(".gradle.kts")

    fun getCommonBuildClasspath(): Set<Path> {
        if (defaultBuildClasspath.isNotEmpty()) {
            return defaultBuildClasspath
        }
        // KLS takes build classpath from temporary settings build file to provide correct compilation on initial stage
        val tempDir: Path = Files.createTempDirectory("temp-dir").toAbsolutePath()
        val settingsBuildFile = tempDir.resolve("settings.gradle.kts")
        Files.write(settingsBuildFile, "".toByteArray())

        val models = invokeTAPI(tempDir.toFile()).second
        val classpath = models.entries.first().value.classPath
        return classpath.map { it.toPath() }.toSet()
    }

    fun removeWorkspace(pathToWorkspace: Path){
        buildEnvByFile = buildEnvByFile.filter {!it.key.startsWith(pathToWorkspace)}.toMutableMap()
    }

    private fun containsSettingsFile(path: Path): Boolean {
        val directory = path.toFile()
        if (directory.isDirectory) {
            return directory.listFiles().any { it.name == "settings.gradle.kts" }
        }
        return false
    }

    private fun getWorkspaceForCall(workspace: Path): Path? {
        val parent = CompositeFinder.findParent(workspace)
        if (parent != null) {
            LOG.info { "parent for $workspace is $parent" }
            return parent
        }

        if (containsSettingsFile(workspace)) {
            LOG.info { "$workspace doesn't have parent, but contains settings file" }
            return workspace
        }
        return null
    }

    private fun makeTapiCall(workspace: File) {
        val (success, scriptModelByFile) = invokeTAPI(workspace)
        LOG.info { "[success=$success] TAPI invoking for $workspace" }
        if (!success) {
            TAPICallFailed = true
            return
        }

        for ((file, model) in scriptModelByFile) {
            val classpath = model.classPath.map { it.toPath() }.let { HashSet(it) }
            defaultBuildClasspath += classpath
            buildEnvByFile[file.toPath()] = CompilationEnvironment(emptySet(), classpath)
        }
    }

    private fun invokeTAPI(pathToDirs: File): Pair<Boolean, Map<File, KotlinDslScriptModel>> {
        GradleConnector.newConnector().forProjectDirectory(pathToDirs).connect().use {
            return try {
                val action = CompositeModelQueryKotlin(KotlinDslScriptsModel::class.java)
                val result = it.action(action).run()
                val models = LinkedHashMap<File, KotlinDslScriptModel>()
                result?.values?.forEach { kotlinDslScriptsModel ->
                    run {
                        val model = kotlinDslScriptsModel.scriptModels
                        models.putAll(model)
                    }
                }
                var modelsString = ""
                models.keys.forEach { file -> modelsString += file.toString() + "\n" }
                LOG.info { "models : ${modelsString}" }
                Pair(true, models)
            } catch (e: Exception) {
                initializeErrorMessage(e)
//                val stackTrace = e.stackTraceToString()
                Pair(false, emptyMap())
            }
        }
    }

    private fun initializeErrorMessage(e: Exception){
        // take message before last because it's full of information about file and it's errors
        var lastMessage = e.message
        var previousMessage = e.message
        var cause = e.cause
        while (cause != null) {
            previousMessage = lastMessage
            lastMessage = cause.message
            cause = cause.cause
        }
        error.message = "Fix errors and save the file \n\n $previousMessage"
    }


}
