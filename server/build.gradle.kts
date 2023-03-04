import groovy.lang.MissingPropertyException

plugins {
    kotlin("jvm")
    id("maven-publish")
    id("application")
    id("com.github.jk1.tcdeps")
    id("com.jaredsburrows.license")
}

val debugPort = 8000
val debugArgs = "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n,quiet=y"
val javaVersion = try {
    project.property("javaVersion").toString()
} catch (_: MissingPropertyException) {
    "11"
}

val serverMainClassName = "org.javacs.kt.MainKt"
val applicationName = "kotlin-language-server"

application {
    mainClass.set(serverMainClassName)
    description = "Code completions, diagnostics and more for Kotlin"
    applicationDefaultJvmArgs = listOf("-DkotlinLanguageServer.version=$version")
    applicationDistribution.into("bin") {
        fileMode = 755
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
    // in /gradle/platform/build.gradle.kts
    implementation(platform("dev.fwcd.kotlin-language-server:platform"))
    annotationProcessor(platform("dev.fwcd.kotlin-language-server:platform"))

    implementation(project(":shared"))

    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc")
    implementation(kotlin("compiler"))
    implementation(kotlin("scripting-compiler"))
    implementation(kotlin("scripting-jvm-host-unshaded"))
    implementation(kotlin("sam-with-receiver-compiler-plugin"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains:fernflower")
    implementation("org.jetbrains.exposed:exposed-core")
    implementation("org.jetbrains.exposed:exposed-dao")
    implementation("org.jetbrains.exposed:exposed-jdbc")
    implementation("com.h2database:h2")
    implementation("com.github.fwcd.ktfmt:ktfmt")
    implementation("com.beust:jcommander")

    testImplementation("org.hamcrest:hamcrest-all")
    testImplementation("junit:junit")
    testImplementation("org.openjdk.jmh:jmh-core")

    // See https://github.com/JetBrains/kotlin/blob/65b0a5f90328f4b9addd3a10c6f24f3037482276/libraries/examples/scripting/jvm-embeddable-host/build.gradle.kts#L8
    compileOnly(kotlin("scripting-jvm-host"))
    testCompileOnly(kotlin("scripting-jvm-host"))

    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess")
}

configurations.forEach { config ->
    config.resolutionStrategy {
        preferProjectModules()
    }
}

tasks.startScripts {
    applicationName = "kotlin-language-server"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        jvmTarget = javaVersion
    }
}

tasks.register<Exec>("fixFilePermissions") {
    // When running on macOS or Linux the start script
    // needs executable permissions to run.

    onlyIf { !System.getProperty("os.name").toLowerCase().contains("windows") }
    commandLine("chmod", "+x", "${tasks.installDist.get().destinationDir}/bin/kotlin-language-server")
}

tasks.register<JavaExec>("debugRun") {
    mainClass.set(serverMainClassName)
    classpath(sourceSets.main.get().runtimeClasspath)
    standardInput = System.`in`

    jvmArgs(debugArgs)
    doLast {
        println("Using debug port $debugPort")
    }
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

// override the distribution name from server-<version>.zip to server.zip
tasks.distZip.get().archiveFileName.set("${project.name}.zip")

tasks.installDist {
    finalizedBy("fixFilePermissions")
}

tasks.build {
    finalizedBy("installDist")
}
