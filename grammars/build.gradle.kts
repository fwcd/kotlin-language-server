plugins {
    distribution
}

distributions {
    main {
        distributionClassifier.set("classifier")
        contents {
            from(projectDir) {
                include("*.json")
            }
        }
    }
}

tasks.getByName<Zip>("distZip") {
    archiveFileName.set("${project.name}.zip")
}
