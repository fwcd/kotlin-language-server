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
    implementation(kotlin("stdlib"))
    testImplementation("org.hamcrest:hamcrest-all")
    testImplementation("junit:junit")
}
