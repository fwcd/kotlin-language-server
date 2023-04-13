plugins {
    distribution
    id("kotlin-language-server.distribution-conventions")
}

distributions {
    main {
        contents {
            from(projectDir) {
                include("*.json")
            }
        }
    }
}
