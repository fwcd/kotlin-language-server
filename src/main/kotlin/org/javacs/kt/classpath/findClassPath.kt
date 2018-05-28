package org.javacs.kt.classpath

import java.util.logging.Level
import org.javacs.kt.LOG
import org.jetbrains.kotlin.utils.ifEmpty
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors
import java.util.function.BiPredicate
import java.util.Comparator

fun findClassPath(workspaceRoots: Collection<Path>): Set<Path> =
        workspaceRoots
                .flatMap { pomFiles(it) }
                .flatMap { readPom(it) }
                .toSet()
                .ifEmpty { backupClassPath() }

private fun backupClassPath() =
    listOfNotNull(findKotlinStdlib()).toSet()

private fun pomFiles(workspaceRoot: Path): Set<Path> =
        Files.walk(workspaceRoot)
                .filter { it.endsWith("pom.xml") }
                .collect(Collectors.toSet())

private fun readPom(pom: Path): Set<Path> {
    val mavenOutput = genDependencyList(pom)
    val artifacts = readDependencyList(mavenOutput)

    when {
        artifacts.isEmpty() -> LOG.warning("No artifacts found in $pom")
        artifacts.size < 5 -> LOG.info("Found ${artifacts.joinToString(", ")} in $pom")
        else -> LOG.info("Found ${artifacts.size} artifacts in $pom")
    }

    return artifacts.mapNotNull({ findArtifact(it, false) }).toSet()
}

private fun genDependencyList(pom: Path): Path {
    val mavenOutput = Files.createTempFile("deps", ".txt")
    val workingDirectory = pom.toAbsolutePath().parent.toFile()
    val cmd = "${mvnCommand()} dependency:list -DincludeScope=test -DoutputFile=$mavenOutput"
    LOG.info("Run ${cmd} in $workingDirectory")
    val status = Runtime.getRuntime().exec(cmd, null, workingDirectory).waitFor()

    assert(status == 0, { "$cmd failed" })

    return mavenOutput
}

private val artifact = Regex(".*:.*:.*:.*:.*")

private fun readDependencyList(mavenOutput: Path): Set<Artifact> =
        mavenOutput.toFile()
                .readLines()
                .filter { it.matches(artifact) }
                .map(::parseArtifact)
                .toSet()

private fun parseArtifact(string: String): Artifact {
    val parts = string.trim().split(':')

    return when (parts.size) {
        3 -> Artifact(parts[0], parts[1], parts[2])
        5 -> Artifact(parts[0], parts[1], parts[3])
        else -> throw IllegalArgumentException("$string is not a properly formed maven artifact")
    }
}

private data class Artifact(val group: String, val artifact: String, val version: String) {
    override fun toString() = "$group:$artifact:$version"
}

private val userHome = Paths.get(System.getProperty("user.home"))
private val mavenHome = userHome.resolve(".m2")

fun findKotlinStdlib(): Path? {
    val group = "org.jetbrains.kotlin"
    val artifact = "kotlin-stdlib"
    val artifactDir = mavenHome.resolve("repository")
            .resolve(group.replace('.', File.separatorChar))
            .resolve(artifact)
    val isKotlinStdlib = BiPredicate<Path, BasicFileAttributes> { file, attr ->
        val name = file.fileName.toString()
        val version = file.parent.fileName.toString()
        val expected = "kotlin-stdlib-${version}.jar"
        name == expected
    }
    val found = Files.find(artifactDir, 2, isKotlinStdlib)
            .sorted(::compareVersions).findFirst().orElse(null)
    if (found == null) LOG.warning("Couldn't find kotlin stdlib in ${artifactDir}")
    else LOG.info("Found kotlin stdlib ${found}")
    return found
}

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

private fun extractVersion(artifact: Path): List<String> {
    return artifact.parent.toString().split(".")
}

private fun findArtifact(a: Artifact, source: Boolean): Path? {
    val result = mavenHome.resolve("repository")
            .resolve(a.group.replace('.', File.separatorChar))
            .resolve(a.artifact)
            .resolve(a.version)
            .resolve(mavenJarName(a, source))

    if (Files.exists(result))
        return result
    else {
        LOG.warning("Couldn't find $a in $result")

        return null
    }
}

private fun mavenJarName(a: Artifact, source: Boolean) =
        if (source) "${a.artifact}-${a.version}-sources.jar"
        else "${a.artifact}-${a.version}.jar"

private var cacheMvnCommand: Path? = null

private fun mvnCommand(): Path {
    if (cacheMvnCommand == null)
        cacheMvnCommand = doMvnCommand()

    return cacheMvnCommand!!
}

private fun doMvnCommand() =
        if (File.separatorChar == '\\') windowsMvnCommand()
        else unixMvnCommand()

private fun windowsMvnCommand() =
        findExecutableOnPath("mvn.cmd") ?: findExecutableOnPath("mvn.bat")

private fun unixMvnCommand() =
        findExecutableOnPath("mvn")

private fun findExecutableOnPath(fileName: String): Path? {
    for (dir in System.getenv("PATH").split(File.pathSeparator)) {
        val file = File(dir, fileName)

        if (file.isFile && file.canExecute()) {
            LOG.info("Found $fileName at ${file.absolutePath}")

            return Paths.get(file.absolutePath)
        }
    }

    return null
}
