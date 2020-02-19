package org.javacs.kt

import org.javacs.kt.classpath.defaultClassPathResolver
import java.io.Closeable
import java.nio.file.Path

class CompilerClassPath(private val config: CompilerConfiguration) : Closeable {
    private val workspaceRoots = mutableSetOf<Path>()
    private val classPath = mutableSetOf<Path>()
    private val buildScriptClassPath = mutableSetOf<Path>()
    var compiler = Compiler(workspaceRoots, classPath, buildScriptClassPath)
        private set

    init {
        compiler.updateConfiguration(config)
    }

    private fun refresh(updateBuildScriptClassPath: Boolean = true) {
        // TODO: Fetch class path and build script class path concurrently (and asynchronously)
        val resolver = defaultClassPathResolver(workspaceRoots)
        var refreshCompiler = false

        val newClassPath = resolver.classpathOrEmpty
        if (newClassPath != classPath) {
            syncClassPath(classPath, newClassPath)
            refreshCompiler = true
        }

        if (updateBuildScriptClassPath) {
            val newBuildScriptClassPath = resolver.buildScriptClasspathOrEmpty
            if (newBuildScriptClassPath != buildScriptClassPath) {
                syncClassPath(buildScriptClassPath, newBuildScriptClassPath)
                refreshCompiler = true
            }
        }

        if (refreshCompiler) {
            compiler.close()
            compiler = Compiler(workspaceRoots, classPath, buildScriptClassPath)
            updateCompilerConfiguration()
        }
    }

    private fun syncClassPath(dest: MutableSet<Path>, new: Set<Path>) {
        val added = new - dest
        val removed = dest - new

        logAdded(added)
        logRemoved(removed)

        dest.removeAll(removed)
        dest.addAll(added)
    }

    fun updateCompilerConfiguration() {
        compiler.updateConfiguration(config)
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
        val name = file.fileName.toString()
        if (name == "pom.xml" || name == "build.gradle" || name == "build.gradle.kts")
            refresh(updateBuildScriptClassPath = false)
    }

    override fun close() {
        compiler.close()
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
