package org.javacs.kt.classpath

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.userHome

private fun createPathOrNull(envVar: String): Path? = System.getenv(envVar)?.let(Paths::get)

private val possibleMavenRepositoryPaths =
    sequenceOf(
        createPathOrNull("MAVEN_REPOSITORY"),
        createPathOrNull("MAVEN_HOME")?.let { it.resolve("repository") },
        createPathOrNull("M2_HOME")?.let { it.resolve("repository") },
        userHome.resolve(".m2/repository"),
    )
    .filterNotNull()

internal val mavenRepository: Path? =
    possibleMavenRepositoryPaths.firstOrNull { Files.exists(it) }

internal val gradleHome = createPathOrNull("GRADLE_USER_HOME") ?: userHome.resolve(".gradle")
