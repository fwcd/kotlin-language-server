package org.javacs.kt

import org.hamcrest.Matchers.*
import org.javacs.kt.classpath.parseMavenArtifact
import org.javacs.kt.classpath.Artifact
import org.javacs.kt.classpath.parseMavenSource
import org.junit.Assert.assertThat
import org.junit.Test

class MavenArtifactParsingTest {
    @Test
    fun `parse maven artifacts`() {
        assertThat(parseMavenArtifact("net.sf.json-lib:json-lib:jar:jdk15:2.4:compile"), equalTo(Artifact(
            group = "net.sf.json-lib",
            artifact = "json-lib",
            packaging = "jar",
            classifier = "jdk15",
            version = "2.4",
            scope = "compile",
            source = false
        )))

        assertThat(parseMavenArtifact("io.netty:netty-transport-native-epoll:jar:linux-x86_64:4.1.36.Final:compile"), equalTo(Artifact(
            group = "io.netty",
            artifact = "netty-transport-native-epoll",
            packaging = "jar",
            classifier = "linux-x86_64",
            version = "4.1.36.Final",
            scope = "compile",
            source = false
        )))

        assertThat(parseMavenArtifact("org.codehaus.mojo:my-project:1.0"), equalTo(Artifact(
            group = "org.codehaus.mojo",
            artifact = "my-project",
            packaging = null,
            classifier = null,
            version = "1.0",
            scope = null,
            source = false
        )))

        assertThat(parseMavenArtifact("io.vertx:vertx-sql-client:test-jar:tests:3.8.0-SNAPSHOT:compile"), equalTo(Artifact(
            group = "io.vertx",
            artifact = "vertx-sql-client",
            packaging = "test-jar",
            classifier = "tests",
            version = "3.8.0-SNAPSHOT",
            scope = "compile",
            source = false
        )))
    }

    @Test
    fun `parse maven sources`() {
        assertThat(parseMavenSource("org.springframework.boot:spring-boot-starter:jar:sources:2.4.5"), equalTo(Artifact(
            group = "org.springframework.boot",
            artifact = "spring-boot-starter",
            packaging = "jar",
            classifier = null,
            version = "2.4.5",
            scope = null,
            source = true
        )))
    }
}
