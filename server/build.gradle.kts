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

val serverDebugPort = 4000
val debugPort = 8000
val debugArgs = "-agentlib:jdwp=transport=dt_socket,server=y,address=$debugPort,suspend=n,quiet=y"

val serverMainClassName = "org.javacs.kt.MainKt"
val applicationName = "kotlin-language-server"

application {
    mainClass.set(serverMainClassName)
    description = "Code completions, diagnostics and more for Kotlin"
    applicationDefaultJvmArgs = listOf("-DkotlinLanguageServer.version=$version")
    applicationDistribution.into("bin") {
        //fileMode = 755
        filePermissions {
            user {
                read = true
                execute = true
                write = true
            }
            other {
                read = true
                write = false
                execute = true
            }
        }
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
    implementation(libs.com.google.guava)

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
    group = "Distribution"
    description = "Fix file permissions for the start script on macOS or Linux."

    onlyIf { !System.getProperty("os.name").lowercase().contains("windows") }
    commandLine(
            "chmod",
            "+x",
            "${tasks.installDist.get().destinationDir}/bin/kotlin-language-server"
    )
}

tasks.register<JavaExec>("debugRun") {
    group = "Application"
    description = "Run the application with debugging enabled."
    mainClass.set(serverMainClassName)
    classpath(sourceSets.main.get().runtimeClasspath)
    standardInput = System.`in`

    jvmArgs(debugArgs)
    args(listOf("--tcpServerPort", serverDebugPort, "--tcpDebug", "--fullLog"))
    doLast { println("Using debug port $debugPort") }
}

tasks.register<CreateStartScripts>("debugStartScripts") {
    group = "Distribution"
    description = "Create start scripts with debug options for the application."
    applicationName = "kotlin-language-server"
    mainClass.set(serverMainClassName)
    outputDir = tasks.installDist.get().destinationDir.toPath().resolve("bin").toFile()
    classpath = tasks.startScripts.get().classpath
    defaultJvmOpts = listOf(debugArgs)
}

tasks.register<Sync>("installDebugDist") {
    group = "Distribution"
    description = "Install the debug distribution and create debug start scripts."
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
