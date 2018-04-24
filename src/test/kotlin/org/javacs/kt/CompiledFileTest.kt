package org.javacs.kt

import org.junit.Assert.assertThat
import org.junit.Test
import org.hamcrest.Matchers.*
import java.nio.file.Files

class CompiledFileTest {
    val compiledFile = compileFile()

    fun compileFile(): CompiledFile {
        val compiler = Compiler(setOf())
        val file = testResourcesRoot().resolve("compiledFile/CompiledFileExample.kt")
        val content = Files.readAllLines(file).joinToString("\n")
        val parse = compiler.createFile(file, content)
        val classPath = CompilerClassPath()
        val sourcePath = listOf(parse)
        val (context, container) = compiler.compileFiles(sourcePath, sourcePath)
        return CompiledFile(content, parse, context, container, sourcePath, classPath)
    }

    @Test fun `typeAtPoint should return type for x`() {
        val type = compiledFile.typeAtPoint(87)!!

        assertThat(type.toString(), equalTo(""))
    }
}