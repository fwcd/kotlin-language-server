pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/kotlin.bintray.com/kotlin-plugin")
    }
}

rootProject.name = "kotlin-language-server"

include(
    "platform",
    "shared",
    "server"
)
