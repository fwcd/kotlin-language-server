package org.javacs.kt

import java.nio.file.Path
import kotlin.io.path.exists

class CompositeFinder {

    companion object {
        fun findParent(pathToWorkspace: Path): Path? {
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
            val result = pathToWorkspace.resolve(relativePathToParent!!).normalize()
            LOG.info { "parent is $result" }
            return result
        }
    }
}
