package org.javacs.kt.util

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import java.nio.file.Path

object ToolingAPI {
    fun isKtsBuildScript(file: Path): Boolean =
        file.fileName.toString().let { it == "build.gradle.kts" }

    operator fun invoke(pathToFile: Path): Set<Path> {
        val projectDir = pathToFile.parent.toFile()
        val connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()

        try {
            val model = connection.getModel(KotlinDslScriptsModel::class.java)
            val scriptModels = model.scriptModels

            val firstScriptModel = scriptModels[pathToFile.toFile()]
            val classpath = firstScriptModel?.classPath
            return classpath?.map { it.toPath() }?.let { HashSet(it) } ?: emptySet()
        } catch (_: Exception) {
            return emptySet()
        } finally {
            connection.close()
        }
    }


}
