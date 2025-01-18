import java.util.Properties
import groovy.lang.MissingPropertyException

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// Use the same Java version for compiling the convention plugins as for the main project
// For this we need to manually load the gradle.properties, since variables from the main
// project are generally not visible from buildSrc.

val javaVersionProperty = "javaVersion"
val javaVersion = try {
    project.property(javaVersionProperty) as String
} catch (e: MissingPropertyException) {
    Properties().also { properties ->
        File("$rootDir/../gradle.properties").inputStream().use { stream ->
            properties.load(stream)
        }
    }[javaVersionProperty] as String
}

kotlin {
    jvmToolchain(javaVersion.toInt())
}

dependencies {
    implementation(libs.org.jetbrains.kotlin.gradle.plugin)
}
