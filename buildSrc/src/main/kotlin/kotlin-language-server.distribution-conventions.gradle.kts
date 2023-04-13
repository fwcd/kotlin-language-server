plugins {
    distribution
}

tasks.distTar {
    archiveFileName.set("${project.name}.tar")
}

tasks.distZip {
    archiveFileName.set("${project.name}.zip")
}
