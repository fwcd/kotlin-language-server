plugins {
    id("maven-publish")
    kotlin("jvm")
    id("kotlin-language-server.publishing-conventions")
    id("kotlin-language-server.kotlin-conventions")
}

repositories {
    mavenCentral()
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
