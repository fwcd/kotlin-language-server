pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/kotlin.bintray.com/kotlin-plugin")
    }
    plugins {
        kotlin("jvm") version "2.1.0" apply false
    }
}

rootProject.name = "kotlin-language-server"

include(
    "platform",
    "shared",
    "server"
)
