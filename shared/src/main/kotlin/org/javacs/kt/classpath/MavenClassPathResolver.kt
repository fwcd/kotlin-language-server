package org.javacs.kt.classpath

import org.javacs.kt.LOG
import org.javacs.kt.util.findCommandOnPath
import org.javacs.kt.util.findProjectCommandWithName
import org.javacs.kt.util.execAndReadStdoutAndStderr
import java.nio.file.Path
import java.nio.file.Files
import java.io.File

/** Resolver for reading maven dependencies */
internal class MavenClassPathResolver private constructor(private val pom: Path) : ClassPathResolver {
    private var artifacts: Set<Artifact>? = null

    override val resolverType: String = "Maven"
    override val classpath: Set<ClassPathEntry> get() {
        val dependenciesOutput = generateMavenDependencyList(pom)
        val artifacts = readMavenDependencyList(dependenciesOutput)

        when {
            artifacts.isEmpty() -> LOG.warn("No artifacts found in {}", pom)
            artifacts.size < 5 -> LOG.info("Found {} in {}", artifacts, pom)
            else -> LOG.info("Found {} artifacts in {}", artifacts.size, pom)
        }

        Files.deleteIfExists(dependenciesOutput)

        this.artifacts = artifacts
        return artifacts.mapNotNull { findMavenArtifact(it, false)?.let { it1 -> ClassPathEntry(it1, null) } }.toSet()
    }

    override val classpathWithSources: Set<ClassPathEntry> get() {
        // Fetch artifacts if not yet present.
        var artifacts: Set<Artifact>
        if (this.artifacts != null) {
            artifacts = this.artifacts!!
        } else {
            val dependenciesOutput = generateMavenDependencyList(pom)
            artifacts = readMavenDependencyList(dependenciesOutput)

            Files.deleteIfExists(dependenciesOutput)
        }

        // Fetch the sources and update the source flag for each artifact.
        val sourcesOutput = generateMavenDependencySourcesList(pom)
        artifacts = readMavenDependencyListWithSources(artifacts, sourcesOutput)

        Files.deleteIfExists(sourcesOutput)
        return artifacts.mapNotNull {
            findMavenArtifact(it, false)?.let {
                it1 -> ClassPathEntry(it1, if (it.source) findMavenArtifact(it, it.source) else null)
            }
        }.toSet()
    }

    companion object {
        /** Create a maven resolver if a file is a pom. */
        fun maybeCreate(file: Path): MavenClassPathResolver? =
            file.takeIf { it.endsWith("pom.xml") }?.let { MavenClassPathResolver(it) }
    }
}

private val artifactPattern = "^[^:]+:(?:[^:]+:)+[^:]+".toRegex()

private fun readMavenDependencyList(dependenciesOutput: Path): Set<Artifact> =
    dependenciesOutput.toFile()
        .readLines()
        .filter { it.matches(artifactPattern) }
        .map { parseMavenArtifact(it) }
        .toSet()

private fun readMavenDependencyListWithSources(artifacts: Set<Artifact>, sourcesOutput: Path): Set<Artifact> {
    val sources = sourcesOutput.toFile()
        .readLines()
        .filter { it.matches(artifactPattern) }
        .mapNotNull { parseMavenSource(it) }
        .toSet()

    artifacts.forEach { it.source = sources.any {
        it1 -> it1.group == it.group && it1.artifact == it.artifact && it1.version == it.version
    }}

    return artifacts
}

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

private fun generateMavenDependencyList(pom: Path): Path {
    val mavenOutput = Files.createTempFile("deps", ".txt")
    val command = listOf(mvnCommand(pom).toString(), "dependency:list", "-DincludeScope=test", "-DoutputFile=$mavenOutput", "-Dstyle.color=never")
    runCommand(pom, command)
    return mavenOutput
}

private fun generateMavenDependencySourcesList(pom: Path): Path {
    val mavenOutput = Files.createTempFile("sources", ".txt")
    val command = listOf(mvnCommand(pom).toString(), "dependency:sources", "-DincludeScope=test", "-DoutputFile=$mavenOutput", "-Dstyle.color=never")
    runCommand(pom, command)
    return mavenOutput
}

private fun runCommand(pom: Path, command: List<String>) {
    val workingDirectory = pom.toAbsolutePath().parent
    LOG.info("Run {} in {}", command, workingDirectory)
    val (result, errors) = execAndReadStdoutAndStderr(command, workingDirectory)
    LOG.debug(result)
    if ("BUILD FAILURE" in errors) {
        LOG.warn("Maven task failed: {}", errors.lines().firstOrNull())
    }
}

private val mvnCommandFromPath: Path? by lazy {
    findCommandOnPath("mvn")
}

private fun mvnCommand(pom: Path): Path {
    return requireNotNull(mvnCommandFromPath ?: findProjectCommandWithName("mvnw", pom)?.also {
        LOG.info("Using mvn wrapper (mvnw) in place of mvn command")
    }) { "Unable to find the 'mvn' command or suitable wrapper" }
}

fun parseMavenArtifact(rawArtifact: String, version: String? = null): Artifact {
    val parts = rawArtifact.trim().split(':')

    return when (parts.size) {
        3 -> Artifact(
            group = parts[0],
            artifact = parts[1],
            packaging = null,
            classifier = null,
            version = version ?: parts[2],
            scope = null,
            source = false
        )
        4 -> Artifact(
            group = parts[0],
            artifact = parts[1],
            packaging = parts[2],
            classifier = null,
            version = version ?: parts[3],
            scope = null,
            source = false
        )
        5 -> Artifact(
            group = parts[0],
            artifact = parts[1],
            packaging = parts[2],
            classifier = null,
            version = version ?: parts[3],
            scope = parts[4],
            source = false
        )
        6 -> Artifact(
            group = parts[0],
            artifact = parts[1],
            packaging = parts[2],
            classifier = parts[3],
            version = version ?: parts[4],
            scope = parts[5],
            source = false
        )
        else -> throw IllegalArgumentException("$rawArtifact is not a properly formed Maven/Gradle artifact")
    }
}

fun parseMavenSource(rawArtifact: String, version: String? = null): Artifact? {
    val parts = rawArtifact.trim().split(':')

    return when (parts.size) {
        5 -> if (parts[3] == "sources") Artifact(
                group = parts[0],
                artifact = parts[1],
                packaging = parts[2],
                classifier = null,
                version = version ?: parts[4].split(" ")[0], // Needed to avoid the rest of the line from being captured.
                scope = null,
                source = true
             ) else null
        else -> null
    }
}

data class Artifact(
    val group: String,
    val artifact: String,
    val packaging: String?,
    val classifier: String?,
    val version: String,
    val scope: String?,
    var source: Boolean
) {
    override fun toString() = "$group:$artifact:$version"
}
