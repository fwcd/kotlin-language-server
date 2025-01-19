plugins {
    kotlin("jvm")
    id("maven-publish")
    id("application")
    alias(libs.plugins.com.github.jk1.tcdeps)
    alias(libs.plugins.com.jaredsburrows.license)
    id("kotlin-language-server.publishing-conventions")
    id("kotlin-language-server.distribution-conventions")
    id("kotlin-language-server.kotlin-conventions")
}

val debugPort = 8000
val debugArgs = "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n,quiet=y"

val serverMainClassName = "org.javacs.kt.MainKt"
val applicationName = "kotlin-language-server"

application {
    mainClass.set(serverMainClassName)
    description = "Code completions, diagnostics and more for Kotlin"
    applicationDefaultJvmArgs = listOf("-DkotlinLanguageServer.version=$version")
    applicationDistribution.into("bin") {
        filePermissions { unix("755".toInt(radix = 8)) }
    }
}

repositories {
    maven(url = "https://repo.gradle.org/gradle/libs-releases")
    maven { url = uri("$projectDir/lib") }
    maven(uri("$projectDir/lib"))
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    // dependencies are constrained to versions defined
    // in /platform/build.gradle.kts
    implementation(platform(project(":platform")))
    annotationProcessor(platform(project(":platform")))

    implementation(project(":shared"))

    implementation(libs.org.eclipse.lsp4j.lsp4j)
    implementation(libs.org.eclipse.lsp4j.jsonrpc)

    // used to clear the error during console log
    implementation(libs.org.slf4j.api)
    implementation(libs.org.slf4j.simple)

    implementation(kotlin("compiler"))
    implementation(kotlin("scripting-compiler"))
    implementation(kotlin("scripting-jvm-host-unshaded"))
    implementation(kotlin("sam-with-receiver-compiler-plugin"))
    implementation(kotlin("reflect"))
    implementation(libs.org.jetbrains.fernflower)
    implementation(libs.org.jetbrains.exposed.core)
    implementation(libs.org.jetbrains.exposed.dao)
    implementation(libs.org.jetbrains.exposed.jdbc)
    implementation(libs.com.h2database.h2)
    implementation(libs.com.github.fwcd.ktfmt)
    implementation(libs.com.beust.jcommander)
    implementation(libs.org.xerial.sqlite.jdbc)

    testImplementation(libs.hamcrest.all)
    testImplementation(libs.junit.junit)
    testImplementation(libs.org.openjdk.jmh.core)

    // See
    // https://github.com/JetBrains/kotlin/blob/65b0a5f90328f4b9addd3a10c6f24f3037482276/libraries/examples/scripting/jvm-embeddable-host/build.gradle.kts#L8
    compileOnly(kotlin("scripting-jvm-host"))
    testCompileOnly(kotlin("scripting-jvm-host"))

    annotationProcessor(libs.org.openjdk.jmh.generator.annprocess)
}

configurations.forEach { config -> config.resolutionStrategy { preferProjectModules() } }

tasks.startScripts { applicationName = "kotlin-language-server" }

tasks.register<Exec>("fixFilePermissions") {
    // When running on macOS or Linux the start script
    // needs executable permissions to run.

    onlyIf { !System.getProperty("os.name").lowercase().contains("windows") }
    commandLine(
            "chmod",
            "+x",
            "${tasks.installDist.get().destinationDir}/bin/kotlin-language-server"
    )
}

tasks.register<JavaExec>("debugRun") {
    mainClass.set(serverMainClassName)
    classpath(sourceSets.main.get().runtimeClasspath)
    standardInput = System.`in`

    jvmArgs(debugArgs)
    doLast { println("Using debug port $debugPort") }
}

tasks.register<CreateStartScripts>("debugStartScripts") {
    applicationName = "kotlin-language-server"
    mainClass.set(serverMainClassName)
    outputDir = tasks.installDist.get().destinationDir.toPath().resolve("bin").toFile()
    classpath = tasks.startScripts.get().classpath
    defaultJvmOpts = listOf(debugArgs)
}

tasks.register<Sync>("installDebugDist") {
    dependsOn("installDist")
    finalizedBy("debugStartScripts")
}

tasks.withType<Test>() {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.installDist { finalizedBy("fixFilePermissions") }

tasks.build { finalizedBy("installDist") }
