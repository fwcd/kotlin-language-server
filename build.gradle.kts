plugins {
    kotlin("jvm") version BuildConstants.kotlinVersion
    `maven-publish`
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
