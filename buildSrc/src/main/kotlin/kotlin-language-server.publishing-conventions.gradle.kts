plugins {
    id("kotlin-language-server.publishing-repository-conventions")
}

publishing {
    publications {
        register("gpr", MavenPublication::class) {
            from(components["java"])
        }
    }
}
