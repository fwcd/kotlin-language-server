import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

plugins {
    kotlin("jvm")
    `maven-publish`
    id("io.gitlab.arturbosch.detekt") version "1.22.0"
}

repositories {
    mavenCentral()
}

val kotlinVersion = "1.8.10"
val exposedVersion = "0.37.3"
val lsp4jVersion = "0.15.0"

// Constrain the dependencies that we use to these specific versions.
// When these dependencies are defined in subsequent builds, they will need
// to match the versions defined here.
subprojects {
    apply(plugin = "java-library") // needed to register the api() constraint
    dependencies {
        constraints {
            api("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
            api("org.hamcrest:hamcrest-all:1.3")
            api("junit:junit:4.11")
            api("org.eclipse.lsp4j:org.eclipse.lsp4j:$lsp4jVersion")
            api("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:$lsp4jVersion")
            api("org.jetbrains.kotlin:kotlin-compiler:$kotlinVersion")
            api("org.jetbrains.kotlin:kotlin-scripting-compiler:$kotlinVersion")
            api("org.jetbrains.kotlin:kotlin-scripting-jvm-host-unshaded:$kotlinVersion")
            api("org.jetbrains.kotlin:kotlin-sam-with-receiver-compiler-plugin:$kotlinVersion")
            api("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
            api("org.jetbrains.kotlin:kotlin-jvm:$kotlinVersion")
            api("org.jetbrains:fernflower:1.0")
            api("org.jetbrains.exposed:exposed-core:$exposedVersion")
            api("org.jetbrains.exposed:exposed-dao:$exposedVersion")
            api("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
            api("com.h2database:h2:1.4.200")
            api("com.github.fwcd.ktfmt:ktfmt:b5d31d1")
            api("com.beust:jcommander:1.78")
            api("org.openjdk.jmh:jmh-core:1.20")
            api("org.jetbrains.kotlin:kotlin-scripting-jvm-host:$kotlinVersion")
            api("org.openjdk.jmh:jmh-generator-annprocess:1.20")
        }
    }
}

detekt {
    allRules = false // activate all available (even unstable) rules.
    buildUponDefaultConfig = true // preconfigure defaults
    parallel = true
    config = files("$rootDir/detekt.yml")
    baseline = file("$rootDir/detekt_baseline.xml")
    source = files(projectDir)
}

// Registers a baseline for Detekt.
//
// The way it works is that you set create "baseline" for Detekt
// by running this task. It will then creatae a detekt-baseline.xml which
// contains a list of current issues found within the project.
// Then every time you run the "detekt" task it will only report errors
// that are not in the baseline config.
//
// We should routinely run this task and commit the baseline file as we
// fix detekt issues so that we can prevent regressions.
tasks.register<DetektCreateBaselineTask>("createDetektBaseline") {
    description = "Overrides current baseline."
    buildUponDefaultConfig.set(true)
    ignoreFailures.set(true)
    parallel.set(true)
    setSource(files(projectDir))
    config.setFrom(files("$rootDir/detekt.yml"))
    baseline.set(file("$rootDir/detekt_baseline.xml"))
    include("**/*.kt")
    include("**/*.kts")
    exclude("**/build/**")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_11.toString()
    exclude("**/build/**")
    reports {
        html.required.set(true)
        md.required.set(true)
    }
}

tasks.check.get().dependsOn(tasks.detekt)
