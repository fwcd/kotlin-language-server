import groovy.lang.MissingPropertyException

plugins {
    id("maven-publish")
    kotlin("jvm")
    id("configure-publishing")
}

repositories {
    mavenCentral()
}

version = project.version
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
    implementation("org.jetbrains.exposed:exposed-core")
    implementation("org.jetbrains.exposed:exposed-dao")
    testImplementation("org.hamcrest:hamcrest-all")
    testImplementation("junit:junit")
}
