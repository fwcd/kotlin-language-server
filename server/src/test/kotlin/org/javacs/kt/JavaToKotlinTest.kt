package org.javacs.kt

import org.javacs.kt.j2k.convertJavaToKotlin
import org.junit.Test
import org.junit.Assert.assertThat
import org.hamcrest.Matchers.equalTo
import java.nio.file.Paths

class JavaToKotlinTest : LanguageServerTestFixture("j2k") {
    @Test fun `test j2k conversion`() {
        val javaCode = workspaceRoot
            .resolve("JavaJSONConverter.java")
            .toFile()
            .readText()
            .trim()
        val expectedKotlinCode = workspaceRoot
            .resolve("JavaJSONConverter.kt")
            .toFile()
            .readText()
            .trim()
        val compiler = languageServer.classPath.compiler
        val convertedKotlinCode = convertJavaToKotlin(javaCode, compiler)
        assertThat(convertedKotlinCode, equalTo(expectedKotlinCode))
    }
}
