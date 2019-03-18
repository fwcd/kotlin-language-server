package org.javacs.kt

import org.javacs.kt.classpath.findClassPath
import java.nio.file.Path

class CompilerClassPath {
    private val workspaceRoots = mutableSetOf<Path>()
    private val classPath = mutableSetOf<Path>()
    var compiler = Compiler(classPath)
        private set

    private fun refresh() {
        val newClassPath = findClassPath(workspaceRoots)

        if (newClassPath != classPath) {
            val added = newClassPath - classPath
            val removed = classPath - newClassPath

            logAdded(added)
            logRemoved(removed)

            classPath.removeAll(removed)
            classPath.addAll(added)
            compiler = Compiler(classPath)
        }
    }

    fun addWorkspaceRoot(root: Path) {
        LOG.info("Searching for dependencies in workspace root {}", root)

        workspaceRoots.add(root)

        refresh()
    }

    fun removeWorkspaceRoot(root: Path) {
        LOG.info("Remove dependencies from workspace root {}", root)

        workspaceRoots.remove(root)

        refresh()
    }

    fun createdOnDisk(file: Path) {
        changedOnDisk(file)
    }

    fun deletedOnDisk(file: Path) {
        changedOnDisk(file)
    }

    fun changedOnDisk(file: Path) {
        if (file.fileName.toString() == "pom.xml")
            refresh()
    }
}

private fun logAdded(sources: Collection<Path>) {
    when {
        sources.isEmpty() -> return
        sources.size > 5 -> LOG.info("Adding {} files to class path", sources.size)
        else -> LOG.info("Adding {} to class path", sources)
    }
}

private fun logRemoved(sources: Collection<Path>) {
    when {
        sources.isEmpty() -> return
        sources.size > 5 -> LOG.info("Removing {} files from class path", sources.size)
        else -> LOG.info("Removing {} from class path", sources)
    }
}
