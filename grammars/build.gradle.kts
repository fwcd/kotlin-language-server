val distZip by tasks.registering(Zip::class) {
    archiveFileName.set("grammars.zip")
    destinationDirectory.set(file("$buildDir/distributions"))
    from(projectDir) {
        include("*.json")
    }
}
