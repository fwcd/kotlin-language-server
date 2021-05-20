package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.javacs.kt.util.execAndReadStdoutAndStderr
import org.javacs.kt.util.findCommandOnPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Resolver for reading maven dependencies */
internal class MavenClassPathResolver private constructor(private val pom: Path) : ClassPathResolver {
    override val resolverType: String = "Maven"
    override val classpath: Set<Path>
        get() {
            val artifacts = generateMavenDependencySet(pom)

            when {
                artifacts.isEmpty() -> LOG.warn("No artifacts found in {}", pom)
                artifacts.size < 5 -> LOG.info("Found {} in {}", artifacts, pom)
                else -> LOG.info("Found {} artifacts in {}", artifacts.size, pom)
            }

            return artifacts.mapNotNull { findMavenArtifact(it, false) }.toSet()
        }
    val sourceJars: Set<Path>
        get() {
            val artifacts = generateMavenSourceJarSet(pom)
            when {
                artifacts.isEmpty() -> LOG.warn("No sources found in {}", pom)
                artifacts.size < 5 -> LOG.info("Found {} sources in {}", artifacts, pom)
                else -> LOG.info("Found {} sources in {}", artifacts.size, pom)
            }
            return artifacts.mapNotNull { findMavenArtifact(it, false) }.toSet()
        }

    companion object {
        /** Create a maven resolver if a file is a pom. */
        fun maybeCreate(file: Path): MavenClassPathResolver? =
            file.takeIf { it.endsWith("pom.xml") }?.let { MavenClassPathResolver(it) }
    }
}

private val artifactPattern = "^[^:]+:(?:[^:]+:)+[^:]+".toRegex()

// Can be used to read sources
private fun readMavenDependencyList(mavenOutput: Path): Set<Artifact> =
    mavenOutput.toFile()
        .readLines()
        .filter { it.matches(artifactPattern) }
        .map { parseMavenArtifact(it) }
        .toSet()

private fun findMavenArtifact(a: Artifact, source: Boolean): Path? {
    val result = mavenHome.resolve("repository")
        .resolve(a.group.replace('.', File.separatorChar))
        .resolve(a.artifact)
        .resolve(a.version)
        .resolve(mavenJarName(a, source))

    return if (Files.exists(result))
        result
    else {
        LOG.warn("Couldn't find {} in {}", a, result)
        null
    }
}

private fun mavenJarName(a: Artifact, source: Boolean) =
    if (source) "${a.artifact}-${a.version}-sources.jar"
    else "${a.artifact}-${a.version}.jar"

private fun generateMavenDependencySet(pom: Path): Set<Artifact> {
    val (_, path) = runMavenCommand(pom, "dependency:list", "-DincludeScope=test")
    val deps = readMavenDependencyList(path)
    Files.deleteIfExists(path)
    return deps
}

private fun generateMavenSourceJarSet(pom: Path): Set<Artifact> {
    val (_, path) = runMavenCommand(pom, "dependency:sources", "")
    val sources = readMavenDependencyList(path)
    Files.deleteIfExists(path)
    return sources
}

private fun runMavenCommand(pom: Path, stage: String, options: String = ""): Pair<Boolean, Path> {
    val output = Files.createTempFile("""${stage}_output""", ".txt")
    val command = """$mvnCommand $stage -DoutputFile=$output $options"""
    val workingDirectory = pom.toAbsolutePath().parent
    LOG.info("Run {} in {}", command, workingDirectory)
    val (result, errors) = execAndReadStdoutAndStderr(command, workingDirectory)
    LOG.debug(result)
    return if ("BUILD FAILURE" in errors) {
        LOG.warn("Maven task failed: {}", errors.lines().firstOrNull())
        Pair(false, output)
    } else {
        Pair(true, output)
    }
}

private val mvnCommand: Path by lazy {
    requireNotNull(findCommandOnPath("mvn")) { "Unable to find the 'mvn' command" }
}

fun parseMavenArtifact(rawArtifact: String, version: String? = null): Artifact {
    var parts = rawArtifact.trim().split(':')
    val source = parts.contains("sources")
    parts = parts.filter { it != "sources" }

    // Creates artifact from parts of parts Array
    return when (parts.size) {
        3 -> Artifact(
            group = parts[0],
            artifact = parts[1],
            packaging = null,
            classifier = null,
            version = version ?: parts[2],
            scope = null,
            source = source
        )
        4 -> Artifact(
            group = parts[0],
            artifact = parts[1],
            packaging = parts[2],
            classifier = null,
            version = version ?: parts[3],
            scope = null,
            source = source
        )
        5 -> Artifact(
            group = parts[0],
            artifact = parts[1],
            packaging = parts[2],
            classifier = null,
            version = version ?: parts[3],
            scope = parts[4],
            source = source
        )
        6 -> Artifact(
            group = parts[0],
            artifact = parts[1],
            packaging = parts[2],
            classifier = parts[3],
            version = version ?: parts[4],
            scope = parts[5],
            source = source
        )
        else -> throw IllegalArgumentException("$rawArtifact is not a properly formed Maven/Gradle artifact")
    }
}

data class Artifact(
    val group: String,
    val artifact: String,
    val packaging: String?,
    val classifier: String?,
    val version: String,
    val scope: String?,
    var source: Boolean = false
) {
    override fun toString() = "$group:$artifact:$version:$packaging:$scope:$classifier:$source"
}
//$source:$classifier
