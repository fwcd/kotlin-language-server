tasks.register<Zip>("distZip") {
    from(projectDir) {
        include("*.json")
    }
    archiveFileName.set("${project.name}.zip")
    destinationDirectory.set(file("$buildDir/distributions"))
}
