package org.javacs.kt.classpath

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.BiPredicate
import org.javacs.kt.util.tryResolving
import org.javacs.kt.util.findCommandOnPath
import java.nio.file.Paths

/** Backup classpath that find Kotlin in the user's Maven/Gradle home or kotlinc's libraries folder. */
object BackupClassPathResolver : ClassPathResolver {
    override val resolverType: String = "Backup"
    override val classpath: Set<ClassPathEntry> get() = findKotlinStdlib()?.let { setOf(it) }.orEmpty().map { ClassPathEntry(it, null) }.toSet()
}

fun findKotlinStdlib(): Path? =
    findLocalArtifact("org.jetbrains.kotlin", "kotlin-stdlib")
    ?: findKotlinCliCompilerLibrary("kotlin-stdlib")

private fun findLocalArtifact(group: String, artifact: String) =
    tryResolving("$artifact using Maven") { tryFindingLocalArtifactUsing(group, artifact, findLocalArtifactDirUsingMaven(group, artifact)) }
    ?: tryResolving("$artifact using Gradle") { tryFindingLocalArtifactUsing(group, artifact, findLocalArtifactDirUsingGradle(group, artifact)) }

private fun tryFindingLocalArtifactUsing(@Suppress("UNUSED_PARAMETER") group: String, artifact: String, artifactDirResolution: LocalArtifactDirectoryResolution): Path? {
    val isCorrectArtifact = BiPredicate<Path, BasicFileAttributes> { file, _ ->
        val name = file.fileName.toString()
        when (artifactDirResolution.buildTool) {
            "Maven" -> {
                val version = file.parent.fileName.toString()
                val expected = "${artifact}-${version}.jar"
                name == expected
            }
            else -> name.startsWith(artifact) && name.endsWith(".jar")
        }
    }
    return Files.list(artifactDirResolution.artifactDir)
        .sorted(::compareVersions)
        .findFirst()
        .orElse(null)
        ?.let {
            Files.find(artifactDirResolution.artifactDir, 3, isCorrectArtifact)
                .findFirst()
                .orElse(null)
        }
}

private data class LocalArtifactDirectoryResolution(val artifactDir: Path?, val buildTool: String)

/** Tries to find the Kotlin command line compiler's standard library. */
private fun findKotlinCliCompilerLibrary(name: String): Path? =
    findCommandOnPath("kotlinc")
        ?.toRealPath()
        ?.parent // bin
        ?.parent // libexec or "top-level" dir
        ?.let {
            // either in libexec or a top-level directory (that may contain libexec, or just a lib-directory directly)
            val possibleLibDir = it.resolve("lib")
            if (Files.exists(possibleLibDir)) {
                possibleLibDir
            } else {
                it.resolve("libexec").resolve("lib")
            }
        }
        ?.takeIf { Files.exists(it) }
        ?.let(Files::list)
        ?.filter { it.fileName.toString() == "$name.jar" }
        ?.findFirst()
        ?.orElse(null)


private fun Path.existsOrNull() =
    if (Files.exists(this)) this else null

private fun findLocalArtifactDirUsingMaven(group: String, artifact: String) =
    LocalArtifactDirectoryResolution(mavenHome.resolve("repository")
        ?.resolve(group.replace('.', File.separatorChar))
        ?.resolve(artifact)
        ?.existsOrNull(), "Maven")

private fun findLocalArtifactDirUsingGradle(group: String, artifact: String) =
    LocalArtifactDirectoryResolution(gradleCaches
        ?.resolve(group)
        ?.resolve(artifact)
        ?.existsOrNull(), "Gradle")


// TODO: Resolve the gradleCaches dynamically instead of hardcoding this path
private val gradleCaches by lazy {
    gradleHome.resolve("caches")
        .resolveStartingWith("modules")
        .resolveStartingWith("files")
}

private fun Path.resolveStartingWith(prefix: String) = Files.list(this).filter { it.fileName.toString().startsWith(prefix) }.findFirst().orElse(null)

private fun compareVersions(left: Path, right: Path): Int {
    val leftVersion = extractVersion(left)
    val rightVersion = extractVersion(right)

    for (i in 0 until Math.min(leftVersion.size, rightVersion.size)) {
        val leftRev = leftVersion[i].reversed()
        val rightRev = rightVersion[i].reversed()
        val compare = leftRev.compareTo(rightRev)
        if (compare != 0)
            return -compare
    }

    return -leftVersion.size.compareTo(rightVersion.size)
}
private fun extractVersion(artifactVersionDir: Path): List<String> {
    return artifactVersionDir.toString().split(".")
}

