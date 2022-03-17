package org.javacs.kt.classpath

import kotlinx.serialization.Serializable
import org.javacs.kt.LOG
import org.javacs.kt.storage.Storage
import org.javacs.kt.util.findCommandOnPath
import org.javacs.kt.util.execAndReadStdoutAndStderr
import java.nio.file.Path
import java.nio.file.Files
import java.io.File

/** Resolver for reading maven dependencies */
internal class MavenClassPathResolver private constructor(private val pom: Path, private val storage: Storage?) : ClassPathResolver {
    private var artifacts: Set<Artifact>? = null

    override val resolverType: String = "Maven"

    private var cachedClassPath: MavenClasspathCache? = storage?.getObject("mavenClasspath")

    override val classpath: Set<ClassPathEntry> get() {
        cachedClassPath?.let { if (!dependenciesChanged()) return it.classpathEntries }

        val dependenciesOutput = generateMavenDependencyList(pom)
        val artifacts = readMavenDependencyList(dependenciesOutput)

        when {
            artifacts.isEmpty() -> LOG.warn("No artifacts found in {}", pom)
            artifacts.size < 5 -> LOG.info("Found {} in {}", artifacts, pom)
            else -> LOG.info("Found {} artifacts in {}", artifacts.size, pom)
        }

        Files.deleteIfExists(dependenciesOutput)

        this.artifacts = artifacts

        val newClasspath = artifacts.mapNotNull { findMavenArtifact(it, false)?.let { it1 -> ClassPathEntry(it1, null) } }.toSet()

        updateClasspathCache(MavenClasspathCache(newClasspath, false))

        return newClasspath
    }

    override val classpathWithSources: Set<ClassPathEntry> get() {
        cachedClassPath?.let { if (!dependenciesChanged() && it.includesSources) return it.classpathEntries }

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
        val newClasspath = artifacts.mapNotNull {
            findMavenArtifact(it, false)?.let {
                it1 -> ClassPathEntry(it1, if (it.source) findMavenArtifact(it, it.source) else null)
            }
        }.toSet()

        updateClasspathCache(MavenClasspathCache(newClasspath, true))

        return newClasspath
    }

    private fun updateClasspathCache(newClasspathCache: MavenClasspathCache) {
        storage?.setObject("mavenClasspath", newClasspathCache)
        storage?.setObject("mavenPomFileVersion", getCurrentPomFileVersion())
        cachedClassPath = newClasspathCache
    }

    private fun dependenciesChanged(): Boolean {
        return storage?.getObject<Long>("mavenPomFileVersion") ?: 0 < getCurrentPomFileVersion()
    }

    private fun getCurrentPomFileVersion(): Long = pom.toFile().lastModified()

    companion object {
        /** Create a maven resolver if a file is a pom. */
        fun maybeCreate(file: Path, storage: Storage?): MavenClassPathResolver? =
            file.takeIf { it.endsWith("pom.xml") }?.let { MavenClassPathResolver(it, storage) }
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
    val command = "$mvnCommand dependency:list -DincludeScope=test -DoutputFile=$mavenOutput -Dstyle.color=never"
    runCommand(pom, command)
    return mavenOutput
}

private fun generateMavenDependencySourcesList(pom: Path): Path {
    val mavenOutput = Files.createTempFile("sources", ".txt")
    val command = "$mvnCommand dependency:sources -DincludeScope=test -DoutputFile=$mavenOutput -Dstyle.color=never"
    runCommand(pom, command)
    return mavenOutput
}

private fun runCommand(pom: Path, command: String) {
    val workingDirectory = pom.toAbsolutePath().parent
    LOG.info("Run {} in {}", command, workingDirectory)
    val (result, errors) = execAndReadStdoutAndStderr(command, workingDirectory)
    LOG.debug(result)
    if ("BUILD FAILURE" in errors) {
        LOG.warn("Maven task failed: {}", errors.lines().firstOrNull())
    }
}

private val mvnCommand: Path by lazy {
    requireNotNull(findCommandOnPath("mvn")) { "Unable to find the 'mvn' command" }
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

@Serializable
private data class MavenClasspathCache(
    val classpathEntries: Set<ClassPathEntry>,
    val includesSources: Boolean
)
