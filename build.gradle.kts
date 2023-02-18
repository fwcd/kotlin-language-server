import io.gitlab.arturbosch.detekt.Detekt

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
    source = files(projectDir)
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
