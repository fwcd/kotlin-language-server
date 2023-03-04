pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/kotlin.bintray.com/kotlin-plugin")
    }

    // Centralize plugin versions.
    // Ensure these plugins use the same version from the /gradle/platform/build.gradle.kts
    // otherwise you'll get an exception during Gradle's configuration phase
    // stating that the plugin with the specified (or unspecified) version
    // cannot be found.
    //
    // Once declared here, subsequent plugin blocks in the build don't require
    // a version to be applied. They inherit the versions from the following
    // block.
    //
    // This can be verified by running the dependencies task via
    // ./gradlew dependencies
    plugins {
        id("application") apply false
        id("maven-publish") apply false

        kotlin("jvm") version "1.8.10" apply false
        id("com.github.jk1.tcdeps") version "1.2" apply false
        id("com.jaredsburrows.license") version "0.8.42" apply false
        id("com.palantir.git-version") version "1.0.0" apply false
    }
}

dependencyResolutionManagement {
    includeBuild("gradle/platform")
}

rootProject.name = "kotlin-language-server"

include(
    "shared",
    "server",
    "grammars"
)
