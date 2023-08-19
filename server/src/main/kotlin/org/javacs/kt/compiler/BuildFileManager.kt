package org.javacs.kt.compiler

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.javacs.kt.CompositeFinder
import org.javacs.kt.CompositeModelQuery

import org.javacs.kt.LOG
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object BuildFileManager {
    var buildEnvByFile: MutableMap<Path, CompilationEnvironment> = mutableMapOf()

    // workspaces which TAPI was invoked for at least once
    private var localWorkspaces = mutableSetOf<Path>()

    private var TAPICallFailed = false

    private val error = Diagnostic(Range(Position(0, 0), Position(0, 0)), String())

    private var defaultBuildClasspath = emptySet<Path>()

    private fun createDefaultModel(): KotlinDslScriptModel {
        // KLS takes build classpath from temporary settings build file
        // to provide correct default classpath and correct default implicit imports in CompilationEnvironent
        val tempDir: Path = Files.createTempDirectory("temp-dir").toAbsolutePath()
        val settingsBuildFile = tempDir.resolve("settings.gradle.kts")
        Files.write(settingsBuildFile, "".toByteArray())

        val models = invokeTAPI(tempDir.toFile()).second
        LOG.info { "Default build model has been created" }
        return models.entries.first().value
    }

    val defaultModel: KotlinDslScriptModel = createDefaultModel()

    fun getError(): Diagnostic = error

    fun buildConfContainsError(): Boolean = TAPICallFailed

    fun updateBuildEnvironment(pathToFile: Path, updatedPluginBlock: Boolean = false) {
        val workspace = pathToFile.parent
        LOG.info { "UPDATE build env $workspace \n workspaces: \n $localWorkspaces" }

        val workspaceForCall = CompositeFinder.getWorkspaceForCall(workspace) ?: return

        if (localWorkspaces.contains(workspace) && !updatedPluginBlock) return
        localWorkspaces.add(workspaceForCall)

        makeTapiCall(workspaceForCall.toFile())
    }

    fun updateBuildEnvironments() {
        LOG.info {
            "UPDATE all build environments \n" +
                " workspaces: \n" +
                " $localWorkspaces"
        }

        for (workspace in localWorkspaces) {
            makeTapiCall(workspace.toFile())
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
        return defaultModel.classPath.map { it.toPath() }.toSet()
    }

    fun removeWorkspace(pathToWorkspace: Path) {
        buildEnvByFile =
            buildEnvByFile.filter { !it.key.startsWith(pathToWorkspace) }.toMutableMap()
        localWorkspaces = localWorkspaces.filter { !it.startsWith(pathToWorkspace) }.toMutableSet()
    }

    private fun makeTapiCall(workspace: File) {
        val (success, scriptModelByFile) = invokeTAPI(workspace)
        LOG.info { "[TAPI success=$success] workspace=$workspace" }
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
        // use last version of gradle because some features isn't supported by default gradle version
        GradleConnector.newConnector().forProjectDirectory(pathToDirs).useGradleVersion("8.2.1")
            .connect().use {
                return try {
                    val action = CompositeModelQuery(KotlinDslScriptsModel::class.java)
                    val result = it.action(action).run()
                    val models = LinkedHashMap<File, KotlinDslScriptModel>()
                    result?.values?.forEach { kotlinDslScriptsModel ->
                        run {
                            val model = kotlinDslScriptsModel.scriptModels
                            models.putAll(model)
                        }
                    }

                    LOG.debug { "models : ${models.keys}" }
                    Pair(true, models)
                } catch (e: Exception) {
                    initializeErrorMessage(e)
//                val stackTrace = e.stackTraceToString()
                    Pair(false, emptyMap())
                }
            }
    }

    private fun initializeErrorMessage(e: Exception) {
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
