package org.javacs.kt.classpath

import org.javacs.kt.LOG
import java.nio.file.Path

/** Resolver for reading maven dependencies */
internal class KScriptClassPathResolver private constructor(private val script: Path) : ClassPathResolver {
    override val resolverType: String = "KScript"
    override val classpath: Set<Path> get() {
        val artifacts = readKScriptDependencyList(script)

        when {
            artifacts.isEmpty() -> LOG.warn("No artifacts found in {}", script)
            artifacts.size < 5 -> LOG.info("Found {} in {}", artifacts, script)
            else -> LOG.info("Found {} artifacts in {}", artifacts.size, script)
        }

        return artifacts.mapNotNull { findMavenArtifact(it, false) }.toSet()
    }

    companion object {
        /** Create a maven resolver if a file is a pom. */
        fun maybeCreate(file: Path): KScriptClassPathResolver? =
            file.takeIf {
                it.toString().endsWith("kts") &&
                    it.toFile().bufferedReader().use { b -> b.readLine().contains("kscript") }
            }?.let { KScriptClassPathResolver(file) }
    }
}

private val artifactPattern = "^//DEPS .*".toRegex()

private fun readKScriptDependencyList(script: Path): Set<Artifact> =
    script.toFile()
        .readLines()
        .filter { artifactPattern.matches(it) }
        .flatMap { it.substring("//DEPS ".length).split(",") }
        .map { parseMavenArtifact(it) }
        .toSet()
