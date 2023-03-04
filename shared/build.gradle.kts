import groovy.lang.MissingPropertyException

plugins {
    id("maven-publish")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

val javaVersion = try {
    project.property("javaVersion").toString()
} catch (_: MissingPropertyException) {
    "11"
}

java {
    toolchain {
        languageVersion.set(
            JavaLanguageVersion.of(javaVersion)
        )
    }
}

dependencies {
    // dependencies are constrained to versions defined
    // in /gradle/platform/build.gradle.kts
    implementation(platform("dev.fwcd.kotlin-language-server:platform"))

    implementation(kotlin("stdlib"))
    testImplementation("org.hamcrest:hamcrest-all")
    testImplementation("junit:junit")
}
