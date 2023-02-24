import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask

// TODO: change the hardcoded 1.6.10 to kotlinVeresion that lives in project props
// We could use a buildSrc script for this.
// See: https://github.com/gradle/kotlin-dsl-samples/issues/802
plugins {
    kotlin("jvm") version "1.8.10"
    `maven-publish`
    id("io.gitlab.arturbosch.detekt") version "1.22.0"
}

repositories {
    mavenCentral()
}

configure(subprojects.filter { it.name != "grammars" }) {
    apply(plugin = "kotlin")
    apply(plugin = "maven-publish")

    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/fwcd/kotlin-language-server")
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_USERNAME")
                    password = project.findProperty("gpr.key") as String? ?: System.getenv("GPR_PASSWORD")
                }
            }
        }

        publications {
            register("gpr", MavenPublication::class) {
                from(components["java"])
            }
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
    exclude("shared/build/**/*.*")
    exclude("server/build/**/*.*")
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        md.required.set(true)
    }
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "11"
}

tasks.check.get().dependsOn(tasks.detekt)
