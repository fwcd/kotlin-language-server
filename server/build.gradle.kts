plugins {
    kotlin("jvm")
    id("maven-publish")
    id("application")
    id("com.github.jk1.tcdeps") version "1.2"
    id("com.jaredsburrows.license") version "0.8.42"
}

val projectVersion = project.property("projectVersion").toString()
val kotlinVersion = project.property("kotlinVersion").toString()
val exposedVersion = project.property("exposedVersion").toString()
val lsp4jVersion = project.property("lsp4jVersion").toString()
val javaVersion = project.property("javaVersion").toString()
val debugPort = 8000
val debugArgs = "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n,quiet=y"

version = projectVersion

val serverMainClassName = "org.javacs.kt.MainKt"
val applicationName = "kotlin-language-server"

application {
    mainClass.set(serverMainClassName)
    description = "Code completions, diagnostics and more for Kotlin"
    applicationDefaultJvmArgs = listOf("-DkotlinLanguageServer.version=$projectVersion")
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
    implementation(project(":shared"))
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:$lsp4jVersion")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:$lsp4jVersion")
    implementation(kotlin("compiler", version = kotlinVersion))
    implementation(kotlin("scripting-compiler", version = kotlinVersion))
    implementation(kotlin("scripting-jvm-host-unshaded", version = kotlinVersion))
    implementation(kotlin("sam-with-receiver-compiler-plugin", version = kotlinVersion))
    implementation(kotlin("reflect", version = kotlinVersion))
    implementation("org.jetbrains:fernflower:1.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.h2database:h2:1.4.200")
    implementation("com.github.fwcd.ktfmt:ktfmt:b5d31d1")
    implementation("com.beust:jcommander:1.78")

    testImplementation("org.hamcrest:hamcrest-all:1.3")
    testImplementation("junit:junit:4.11")
    testImplementation("org.openjdk.jmh:jmh-core:1.20")

    // See https://github.com/JetBrains/kotlin/blob/65b0a5f90328f4b9addd3a10c6f24f3037482276/libraries/examples/scripting/jvm-embeddable-host/build.gradle.kts#L8
    compileOnly(kotlin("scripting-jvm-host", version = kotlinVersion))
    testCompileOnly(kotlin("scripting-jvm-host", version = kotlinVersion))

    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.20")
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

tasks.register<Copy>("copyPropertiesToTestWorkspace") {
    from("$rootDir/gradle.properties")
    into(file("src/test/resources/additionalWorkspace"))
}

tasks.register<Copy>("copyPropertiesToDSLTestWorkspace") {
    from("$rootDir/gradle.properties")
    into(file("src/test/resources/kotlinDSLWorkspace"))
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

tasks.getByName("processTestResources") {
    dependsOn("copyPropertiesToTestWorkspace", "copyPropertiesToDSLTestWorkspace")
}

tasks.withType<Test>() {
    dependsOn("copyPropertiesToTestWorkspace", "copyPropertiesToDSLTestWorkspace")

    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.installDist {
    finalizedBy("fixFilePermissions")
}

tasks.build {
    finalizedBy("installDist")
}
