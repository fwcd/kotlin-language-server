package org.javacs.kt.classpath

import org.javacs.kt.util.userHome
import java.nio.file.Paths


internal val gradleHome =
    System.getenv("GRADLE_USER_HOME")?.let { Paths.get(it) }
        ?: userHome.resolve(".gradle")

internal val mavenHome =
    System.getenv("MAVEN_HOME")?.let { Paths.get(it) }
        ?: System.getenv("M2_HOME")?.let { Paths.get(it) }
        ?: userHome.resolve(".m2")

internal val mavenRepository =
    System.getenv("MAVEN_REPOSITORY")?.let { Paths.get(it) }
        ?: mavenHome.resolve("repository")
