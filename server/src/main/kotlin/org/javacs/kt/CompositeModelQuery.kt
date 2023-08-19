package org.javacs.kt

import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildController
import org.gradle.tooling.model.gradle.GradleBuild
import java.util.*

class CompositeModelQuery<T>(private val modelType: Class<T>) :
    BuildAction<Map<String?, T>?> {
    override fun execute(controller: BuildController): Map<String?, T> {
        val acc: MutableMap<String, T> = HashMap()
        // ':' represents the root build
        collectRootModels(
            controller,
            controller.buildModel,
            acc,
            ":",
            controller.buildModel.rootProject.name
        )
        return acc.filter { it.key != null }
    }

    private fun collectRootModels(
        controller: BuildController,
        build: GradleBuild,
        models: MutableMap<String, T>,
        buildPath: String,
        rootBuildRootProjectName: String
    ) {
        if (models.containsKey(buildPath)) {
            return  // can happen when there's a cycle in the included builds
        }
        models[buildPath] = controller.getModel(build.rootProject, modelType)
        for (includedBuild in build.includedBuilds) {
            val includedBuildRootProjectName = includedBuild.rootProject.name
            if (includedBuildRootProjectName != rootBuildRootProjectName) {
                collectRootModels(
                    controller,
                    includedBuild,
                    models,
                    includedBuildRootProjectName,
                    rootBuildRootProjectName
                )
            }
        }
    }

    override fun hashCode(): Int {
        return Objects.hash(modelType)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val otherModel = other as CompositeModelQuery<*>
        return modelType == otherModel.modelType
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
