plugins {
    kotlin("jvm") version "2.1.0"
}

group = "org.javacs"
version = "1.0-SNAPSHOT"

description = "test-project"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.junit)
}
