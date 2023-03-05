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
    exclude("shared/build/**/*.*")
    exclude("server/build/**/*.*")
    exclude("gradle/plugins/build/**/*.*")
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_11.toString()
    reports {
        html.required.set(true)
        md.required.set(true)
    }
}

tasks.check.get().dependsOn(tasks.detekt)
