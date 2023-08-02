package org.javacs.kt.util

import org.javacs.kt.LOG
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import java.io.File

object ToolingKT {

    private var lastClasspath = HashSet<File>()
    // TODO: remove crutches
    operator fun invoke(projectDir: File = File("/Users/kolavladimirov/runtime-New_configuration(1)/t")): Set<File>  {
        val connection = GradleConnector.newConnector().forProjectDirectory(projectDir).connect()

        try {
            val model = connection.getModel(KotlinDslScriptsModel::class.java)
            val scriptModels = model.scriptModels.entries

            return if (scriptModels.isNotEmpty()) {
                val firstScriptModel = scriptModels.first()

                val classpath = firstScriptModel.value.classPath
                val newElements = classpath.filter { !lastClasspath.contains(it) }
                newElements.forEach { a -> lastClasspath.add(a) }
                HashSet(classpath)
            } else {
                emptySet()
            }
        }
        catch (_: Exception){
            return emptySet()
        }
        finally {
            connection.close()
        }
    }



}
