plugins {
    kotlin("jvm")
}

val javaVersion = property("javaVersion").toString()

kotlin {
    jvmToolchain(javaVersion.toInt())
}
