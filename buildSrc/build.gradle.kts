plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// Use JDK 11 for compiling the convention plugins (this is NOT the project!)

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin")
}
