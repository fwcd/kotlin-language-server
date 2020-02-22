plugins {
    kotlin("jvm")
    maven
    application
    id("com.jaredsburrows.license") version "0.8.42"
}

version = BuildConstants.projectVersion

application {
    mainClassName = "org.javacs.kt.MainKt"
    description = "Code completions, diagnostics and more for Kotlin"
    applicationDistribution.into("bin") {
        fileMode = 755
    }
}

// sourceCompatibility = 1.8
// targetCompatibility = 1.8

val debugPort = 8000
val debugArgs = "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n,quiet=y"

repositories {
    maven(url = "https://repo.gradle.org/gradle/libs-releases")
    maven { url = uri("$projectDir/lib") }
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.7.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.7.0")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable")
    implementation("org.jetbrains.kotlin:kotlin-scripting-compiler-impl-embeddable")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host-embeddable")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains:fernflower:1.0")
    implementation("com.pinterest.ktlint:ktlint-core:0.34.2")
    implementation("com.pinterest.ktlint:ktlint-ruleset-standard:0.34.2")
    implementation("com.beust:jcommander:1.78")

    testImplementation("org.hamcrest:hamcrest-all:1.3")
    testImplementation("junit:junit:4.11")
    testImplementation("org.openjdk.jmh:jmh-core:1.20")

    // See https://github.com/JetBrains/kotlin/blob/65b0a5f90328f4b9addd3a10c6f24f3037482276/libraries/examples/scripting/jvm-embeddable-host/build.gradle.kts#L8
    compileOnly("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
    testCompileOnly("org.jetbrains.kotlin:kotlin-scripting-jvm-host")

    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.20")
}

// TODO
// configurations.all { config ->
//     config.resolutionStrategy {
//         preferProjectModules()
//     }
// }

tasks.startScripts {
    applicationName = "kotlin-language-server"
}

val copyPropertiesToTestWorkspace by tasks.registering(Copy::class) {
    from("$rootDir/gradle.properties")
    into(file("src/test/resources/additionalWorkspace"))
}

val copyPropertiesToDSLTestWorkspace by tasks.registering(Copy::class) {
    from("$rootDir/gradle.properties")
    into(file("src/test/resources/kotlinDSLWorkspace"))
}

val fixFilePermissions by tasks.registering(Exec::class) {
    // When running on macOS or Linux the start script
    // needs executable permissions to run.
    onlyIf { !System.getProperty("os.name").toLowerCase().contains("windows") }
    commandLine("chmod", "+x", "${tasks.getByName<Sync>("installDist").destinationDir}/bin/kotlin-language-server")
}

val debugRun by tasks.registering(JavaExec::class) {
    main = application.mainClassName
    standardInput = System.`in`

    classpath(sourceSets.main.get().runtimeClasspath)

    jvmArgs(debugArgs)
    doLast {
        println("Using debug port $debugPort")
    }
}

val debugStartScripts by tasks.registering(CreateStartScripts::class) {
    applicationName = tasks.getByName<CreateStartScripts>("startScripts").applicationName
    mainClassName = tasks.getByName<CreateStartScripts>("startScripts").mainClassName
    outputDir = tasks.getByName<Sync>("installDist").destinationDir.toPath().resolve("bin").toFile()
    classpath = tasks.getByName<CreateStartScripts>("startScripts").classpath
    defaultJvmOpts = listOf(debugArgs)
}

val installDebugDist by tasks.registering(Sync::class) {
    dependsOn(tasks.installDist)
    finalizedBy(debugStartScripts)
}

// TODO
// tasks.run {
//     standardInput = System.`in`
// }

tasks.test {
    dependsOn(copyPropertiesToTestWorkspace, copyPropertiesToDSLTestWorkspace)

    testLogging {
        events("failed")
        // TODO
        // exceptionFormat("full")
    }
}

tasks.compileKotlin {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.distZip {
    archiveName = "${project.name}.zip"
}

tasks.installDist {
    finalizedBy(fixFilePermissions)
}

tasks.build {
    finalizedBy(tasks.installDist)
}
