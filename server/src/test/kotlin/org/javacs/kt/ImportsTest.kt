package org.javacs.kt

import org.javacs.kt.imports.getImportTextEditEntry
import org.jetbrains.kotlin.name.FqName
import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Test

class ImportTextEditTest : SingleFileTestFixture("imports", "Simple.kt") {

    @Test
    fun `should return normal import name`() {
        val ktFile = languageServer.sourcePath.parsedFile(workspaceRoot.resolve(file).toUri())
        val importName = FqName("org.jetbrains.kotlin.name.FqName")
        val result = getImportTextEditEntry(ktFile, importName)

        assertThat(result.range, equalTo(range(1, 23, 1, 23)))
        assertThat(result.newText, equalTo("\n\nimport org.jetbrains.kotlin.name.FqName"))
    }

    @Test
    fun `should wrap -class- in backticks`() {
        val ktFile = languageServer.sourcePath.parsedFile(workspaceRoot.resolve(file).toUri())
        val importName = FqName("com.class.myMethod")
        val result = getImportTextEditEntry(ktFile, importName)

        assertThat(result.range, equalTo(range(1, 23, 1, 23)))
        assertThat(result.newText, equalTo("\n\nimport com.`class`.myMethod"))
    }

    @Test
    fun `should wrap -fun- in backticks`() {
        val ktFile = languageServer.sourcePath.parsedFile(workspaceRoot.resolve(file).toUri())
        val importName = FqName("com.fun.myMethod")
        val result = getImportTextEditEntry(ktFile, importName)

        assertThat(result.range, equalTo(range(1, 23, 1, 23)))
        assertThat(result.newText, equalTo("\n\nimport com.`fun`.myMethod"))
    }

    @Test
    fun `should wrap multiple built in keywords in backticks`() {
        val ktFile = languageServer.sourcePath.parsedFile(workspaceRoot.resolve(file).toUri())
        val importName = FqName("fun.class.someother.package.method.var.val")
        val result = getImportTextEditEntry(ktFile, importName)

        assertThat(result.range, equalTo(range(1, 23, 1, 23)))
        assertThat(result.newText, equalTo("\n\nimport `fun`.`class`.someother.`package`.method.`var`.`val`"))
    }

    @Test
    fun `should NOT wrap soft keywords or modifiers in backticks`() {
        // tests for a selection of soft keywords and modifiers
        // according to https://kotlinlang.org/docs/keyword-reference.html
        //  both can be used as identifiers. (only hard keywords can not)
        val ktFile = languageServer.sourcePath.parsedFile(workspaceRoot.resolve(file).toUri())
        val importName = FqName("as.annotation.import.by.inner.file.field")
        val result = getImportTextEditEntry(ktFile, importName)

        assertThat(result.range, equalTo(range(1, 23, 1, 23)))
        assertThat(result.newText, equalTo("\n\nimport `as`.annotation.import.by.inner.file.field"))
    }
}
