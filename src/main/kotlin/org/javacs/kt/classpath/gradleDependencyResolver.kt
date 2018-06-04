package org.javacs.kt.classpath

import org.javacs.kt.LOG
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import org.gradle.tooling.*
import org.gradle.tooling.model.*
import org.gradle.tooling.model.eclipse.*
import org.gradle.tooling.model.idea.*
import org.gradle.kotlin.dsl.tooling.models.*

fun readBuildGradle(buildFile: Path): Set<Path> {
    val projectDirectory = buildFile.getParent().toFile()
    val connection = GradleConnector.newConnector()
            .forProjectDirectory(projectDirectory)
            .connect()
    var dependencies: Set<Path> = emptySet()

    try {
        dependencies = readDependenciesViaTask(projectDirectory.toPath())
        LOG.info("Resolved Gradle dependencies using task")
    } catch (e: Exception) {
        try {
            dependencies = readDependenciesViaEclipseProject(connection)
            LOG.info("Resolved Gradle dependencies using Eclipse project model")
        } catch (f: Exception) {
            try {
                dependencies = readDependenciesViaKotlinDSL(connection)
                LOG.info("Resolved Gradle dependencies using Kotlin DSL model")
            } catch (g: Exception) {
                try {
                    dependencies = readDependenciesViaIdeaProject(connection)
                    LOG.info("Resolved Gradle dependencies using IDEA project model")
                } catch (h: Exception) {
                    LOG.warning("BuildExceptions while collecting Gradle dependencies: ${e.message} and ${f.message} and ${g.message} and ${h.message}")
                }
            }
        }
    }

    connection.close()
    return dependencies
}

private fun createTemporaryGradleFile(): File {
    val temp = File.createTempFile("tempGradle", ".config")
    val config = File.createTempFile("classpath", ".gradle")
    val classPathGradleConfig = """
        boolean isResolvable(Configuration conf) {
            // isCanBeResolved was added in Gradle 3.3. Previously, all configurations were resolvable
            if (Configuration.class.declaredMethods.any { it.name == 'isCanBeResolved' }) {
                return conf.canBeResolved
            }
            return true
        }

        File getBuildCacheForDependency(File dependency) {
            String name = dependency.getName()
            String home = System.getProperty("user.home")
            String gradleCache = home + File.separator + '.gradle' + File.separator + 'caches' + File.separator
            if (file(gradleCache).exists()) {
                String include = 'transforms*' + File.separator + '**' + File.separator + name + File.separator + '**' + File.separator + 'classes.jar'
                return fileTree(dir: gradleCache, include: include).files.find { it.isFile() }
            } else {
                return zipTree(dependency).files.find { it.isFile() && it.name.endsWith('jar') }
            }
        }

        task classpath {
            doLast {
                HashSet<String> classpathFiles = new HashSet<String>()
                for (proj in allprojects) {
                    for (conf in proj.configurations) {
                        if (isResolvable(conf)) {
                            for (dependency in conf) {
                                classpathFiles += dependency
                            }
                        }
                    }

                    def rjava = proj.getBuildDir().absolutePath + File.separator + "intermediates" + File.separator + "classes" + File.separator + "debug"
                    def rFiles = new File(rjava)
                    if (rFiles.exists()) {
                        classpathFiles += rFiles
                    }

                    if (proj.hasProperty("android")) {
                        classpathFiles += proj.android.getBootClasspath()
                        if (proj.android.hasProperty("applicationVariants")) {
                            proj.android.applicationVariants.all { v ->
                                if (v.hasProperty("javaCompile")) {
                                    classpathFiles += v.javaCompile.classpath
                                }
                                if (v.hasProperty("compileConfiguration")) {
                                    v.compileConfiguration.each { dependency ->
                                        classpathFiles += dependency
                                    }
                                }
                                if (v.hasProperty("runtimeConfiguration")) {
                                    v.runtimeConfiguration.each { dependency ->
                                        classpathFiles += dependency
                                    }
                                }
                                if (v.hasProperty("getApkLibraries")) {
                                    println v.getApkLibraries()
                                    classpathFiles += v.getApkLibraries()
                                }
                                if (v.hasProperty("getCompileLibraries")) {
                                    classpathFiles += v.getCompileLibraries()
                                }
                            }
                        }

                        if (proj.android.hasProperty("libraryVariants")) {
                            proj.android.libraryVariants.all { v ->
                                classpathFiles += v.javaCompile.classpath.files
                            }
                        }
                    }

                HashSet<String> computedPaths = new HashSet<String>()
                for (dependency in classpathFiles) {
                    if (dependency.name.endsWith("jar")) {
                        println dependency
                    } else if (dependency != null) {
                        println getBuildCacheForDependency(dependency)
                    }
                }

            }
        }
    }
    """
    val bw = config.bufferedWriter()
    bw.write(classPathGradleConfig)
    bw.close()

    val tempWriter = temp.bufferedWriter()
    tempWriter.write(String.format("rootProject{ apply from: '%s'} ", config.getAbsolutePath()));
    tempWriter.close();

    return temp;
}

private var cacheGradleCommand: Path? = null

private fun getGradleCommand(workspace: Path): Path {
    if (cacheGradleCommand == null) {
        cacheGradleCommand = workspace.resolve("gradlew").toAbsolutePath()
    }

    return cacheGradleCommand!!
}

private fun readDependenciesViaTask(directory: Path): Set<Path> {
    val gradle = getGradleCommand(directory)
    if (!gradle.toFile().exists()) return mutableSetOf<Path>()
    val config = createTemporaryGradleFile()

    val gradleCommand = String.format("${gradle} -I ${config.absolutePath} classpath")
    val classpathCommand = Runtime.getRuntime().exec(gradleCommand, null, directory.toFile())

    val stdout = classpathCommand.inputStream
    val reader = stdout.bufferedReader()

    classpathCommand.waitFor()

    val artifact = Pattern.compile("^.+?\\.jar$");
    val dependencies = mutableSetOf<Path>()
    for (dependency in reader.lines()) {
        val line = dependency.toString().trim()

        if (artifact.matcher(line).matches()) {
            dependencies.add(Paths.get(line));
        }
    }

    return dependencies
}

private fun readDependenciesViaEclipseProject(connection: ProjectConnection): Set<Path> {
    val dependencies = mutableSetOf<Path>()
    val project: EclipseProject = connection.getModel(EclipseProject::class.java)

    for (dependency in project.classpath) {
        dependencies.add(dependency.file.toPath())
    }

    return dependencies;
}

private fun readDependenciesViaIdeaProject(connection: ProjectConnection): Set<Path> {
    val dependencies = mutableSetOf<Path>()
    val project: IdeaProject = connection.getModel(IdeaProject::class.java)

    for (child in project.children) {
        for (dependency in child.dependencies) {
            if (dependency is ExternalDependency) {
                dependencies.add(dependency.file.toPath())
            }
        }
    }

    return dependencies;
}

private fun readDependenciesViaKotlinDSL(connection: ProjectConnection): Set<Path> {
    val project: KotlinBuildScriptModel = connection.getModel(KotlinBuildScriptModel::class.java)
    return project.classPath.map { it.toPath() }.toSet()
}
