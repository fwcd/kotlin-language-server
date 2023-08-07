plugins {
    id("maven-publish")
    kotlin("jvm")
    id("kotlin-language-server.publishing-conventions")
    id("kotlin-language-server.kotlin-conventions")
}

repositories {
    mavenCentral()
    maven(url = "https://repo.gradle.org/gradle/libs-releases")
}

dependencies {
    // dependencies are constrained to versions defined
    // in /gradle/platform/build.gradle.kts
    implementation(platform("dev.fwcd.kotlin-language-server:platform"))

    implementation("org.gradle:gradle-tooling-api:7.3")
    implementation(kotlin("stdlib"))
    testImplementation("org.hamcrest:hamcrest-all")
    testImplementation("junit:junit")
}
