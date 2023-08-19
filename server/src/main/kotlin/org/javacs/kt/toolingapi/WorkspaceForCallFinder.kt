package org.javacs.kt.toolingapi

import org.javacs.kt.LOG
import java.nio.file.Path
import kotlin.io.path.exists

object WorkspaceForCallFinder {

    fun getWorkspaceForCall(workspace: Path): Path? {
        val parent = findParent(workspace)
        if (parent != null) {
            LOG.debug { "parent for $workspace is $parent" }
            return parent
        }

        if (containsSettingsFile(workspace)) {
            LOG.debug { "$workspace contains settings file" }
            return workspace
        }
        return null
    }

    private fun findParent(pathToWorkspace: Path): Path? {
        val prefsFile =
            pathToWorkspace.resolve(".settings").resolve("org.eclipse.buildship.core.prefs")
        if (!prefsFile.exists()) return null

        var relativePathToParent: String? = null
        prefsFile.toFile().forEachLine {
            if (it.startsWith("connection.project.dir=")) {
                relativePathToParent = it.substring(it.indexOf('=') + 1)
            }
        }

        if (relativePathToParent == null) return null
        return pathToWorkspace.resolve(relativePathToParent!!).normalize()
    }

    private fun containsSettingsFile(path: Path): Boolean {
        val directory = path.toFile()
        if (directory.isDirectory) {
            return directory.listFiles().any { it.name == "settings.gradle.kts" }
        }
        return false
    }
}
