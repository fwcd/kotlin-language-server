package org.javacs.kt

import java.io.Closeable
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import org.javacs.kt.classpath.ClassPathEntry
import org.javacs.kt.classpath.ClassPathResolver
import org.javacs.kt.classpath.defaultClassPathResolver
import org.javacs.kt.compiler.Compiler
import org.javacs.kt.database.DatabaseService
import org.javacs.kt.util.AsyncExecutor

/**
 * Manages the class path (compiled JARs, etc), the Java source path and the compiler. Note that
 * Kotlin sources are stored in SourcePath.
 */
class CompilerClassPath(
    private val config: CompilerConfiguration,
    private val scriptsConfig: ScriptsConfiguration,
    private val codegenConfig: CodegenConfiguration,
    private val databaseService: DatabaseService,
) : Closeable {
    val workspaceRoots = mutableSetOf<Path>()

    private val javaSourcePath = mutableSetOf<Path>()
    private val buildScriptClassPath = mutableSetOf<Path>()
    val classPath = mutableSetOf<ClassPathEntry>()
    val outputDirectory: File = Files.createTempDirectory("klsBuildOutput").toFile()
    val javaHome: String? = System.getProperty("java.home", null)

    var compiler =
        Compiler(
            javaSourcePath,
            classPath.map { it.compiledJar }.toSet(),
            buildScriptClassPath,
            scriptsConfig,
            codegenConfig,
            outputDirectory,
        )
        private set

    private val async = AsyncExecutor()

    init {
        compiler.updateConfiguration(config)
    }

    /** Updates and possibly reinstantiates the compiler using new paths. */
    private fun refresh(
        updateClassPath: Boolean = true,
        updateBuildScriptClassPath: Boolean = true,
        updateJavaSourcePath: Boolean = true,
    ): Boolean {
        val resolver = defaultClassPathResolver(workspaceRoots, databaseService.db)
        var refreshCompiler = updateJavaSourcePath

        val classPathFuture =
            if (updateClassPath) {
                updateClassPathAsync(resolver)
            } else {
                CompletableFuture.completedFuture(false)
            }

        val buildScriptClassPathFuture =
            if (updateBuildScriptClassPath) {
                updateBuildScriptClassPathAsync(resolver)
            } else {
                CompletableFuture.completedFuture(false)
            }

        CompletableFuture.allOf(classPathFuture, buildScriptClassPathFuture).join()

        refreshCompiler =
            refreshCompiler || classPathFuture.get() || buildScriptClassPathFuture.get()

        if (refreshCompiler) {
            LOG.info("Reinstantiating compiler")
            compiler.close()
            compiler =
                Compiler(
                    javaSourcePath,
                    classPath.map { it.compiledJar }.toSet(),
                    buildScriptClassPath,
                    scriptsConfig,
                    codegenConfig,
                    outputDirectory,
                )
            updateCompilerConfiguration()
        }

        return refreshCompiler
    }

    private fun updateClassPathAsync(resolver: ClassPathResolver): CompletableFuture<Boolean> {
        return async.compute {
            var updated = false
            val newClassPath = resolver.classpathOrEmpty
            if (newClassPath != classPath) {
                synchronized(classPath) {
                    syncPaths(classPath, newClassPath, "class path") { it.compiledJar }
                }
                updated = true
            }

            val newClassPathWithSources = resolver.classpathWithSources
            synchronized(classPath) {
                syncPaths(classPath, newClassPathWithSources, "class path with sources") {
                    it.compiledJar
                }
            }

            updated
        }
    }

    private fun updateBuildScriptClassPathAsync(
        resolver: ClassPathResolver
    ): CompletableFuture<Boolean> {
        return async.compute {
            var updated = false
            LOG.info("Update build script path")
            val newBuildScriptClassPath = resolver.buildScriptClasspathOrEmpty
            if (newBuildScriptClassPath != buildScriptClassPath) {
                synchronized(buildScriptClassPath) {
                    syncPaths(
                        buildScriptClassPath,
                        newBuildScriptClassPath,
                        "build script class path",
                    ) {
                        it
                    }
                }
                updated = true
            }
            updated
        }
    }

    /** Synchronizes the given two path sets and logs the differences. */
    private fun <T> syncPaths(dest: MutableSet<T>, new: Set<T>, name: String, toPath: (T) -> Path) {
        val added = new - dest
        val removed = dest - new

        logAdded(added.map(toPath), name)
        logRemoved(removed.map(toPath), name)

        dest.removeAll(removed)
        dest.addAll(added)
    }

    fun updateCompilerConfiguration() {
        compiler.updateConfiguration(config)
    }

    fun addWorkspaceRoot(root: Path): Boolean {
        LOG.info("Searching for dependencies and Java sources in workspace root {}", root)

        workspaceRoots.add(root)
        javaSourcePath.addAll(findJavaSourceFiles(root))

        return refresh()
    }

    fun removeWorkspaceRoot(root: Path): Boolean {
        LOG.info("Removing dependencies and Java source path from workspace root {}", root)

        workspaceRoots.remove(root)
        javaSourcePath.removeAll(findJavaSourceFiles(root))

        return refresh()
    }

    fun createdOnDisk(file: Path): Boolean {
        if (isJavaSource(file)) {
            javaSourcePath.add(file)
        }
        return changedOnDisk(file)
    }

    fun deletedOnDisk(file: Path): Boolean {
        if (isJavaSource(file)) {
            javaSourcePath.remove(file)
        }
        return changedOnDisk(file)
    }

    fun changedOnDisk(file: Path): Boolean {
        val buildScript = isBuildScript(file)
        val javaSource = isJavaSource(file)
        if (buildScript || javaSource) {
            return refresh(
                updateClassPath = buildScript,
                updateBuildScriptClassPath = false,
                updateJavaSourcePath = javaSource,
            )
        } else {
            return false
        }
    }

    private fun isJavaSource(file: Path): Boolean = file.fileName.toString().endsWith(".java")

    private fun isBuildScript(file: Path): Boolean =
        file.fileName.toString().let {
            it == "pom.xml" || it == "build.gradle" || it == "build.gradle.kts"
        }

    private fun findJavaSourceFiles(root: Path): Set<Path> {
        val sourceMatcher = FileSystems.getDefault().getPathMatcher("glob:*.java")
        return SourceExclusions(listOf(root), scriptsConfig)
            .walkIncluded()
            .filter { sourceMatcher.matches(it.fileName) }
            .toSet()
    }

    override fun close() {
        compiler.close()
        outputDirectory.delete()
    }
}

private fun logAdded(sources: Collection<Path>, name: String) {
    when {
        sources.isEmpty() -> return
        sources.size > 5 -> LOG.info("Adding {} files to {}", sources.size, name)
        else -> LOG.info("Adding {} to {}", sources, name)
    }
}

private fun logRemoved(sources: Collection<Path>, name: String) {
    when {
        sources.isEmpty() -> return
        sources.size > 5 -> LOG.info("Removing {} files from {}", sources.size, name)
        else -> LOG.info("Removing {} from {}", sources, name)
    }
}
