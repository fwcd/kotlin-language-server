plugins {
    kotlin("jvm")
}

val javaVersion = property("javaVersion") as String

kotlin {
    jvmToolchain(javaVersion.toInt())
}
