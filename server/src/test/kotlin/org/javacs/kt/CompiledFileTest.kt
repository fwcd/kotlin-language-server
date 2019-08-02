package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.nio.file.Files

class CompiledFileTest {
    val compiledFile = compileFile()

    fun compileFile(): CompiledFile = Compiler(setOf()).use { compiler ->
        val file = testResourcesRoot().resolve("compiledFile/CompiledFileExample.kt")
        val content = Files.readAllLines(file).joinToString("\n")
        val parse = compiler.createFile(content, file)
        val classPath = CompilerClassPath(CompilerConfiguration())
        val sourcePath = listOf(parse)
        val (context, container) = compiler.compileFiles(sourcePath, sourcePath)
        CompiledFile(content, parse, context, container, sourcePath, classPath)
    }

    @Test fun `typeAtPoint should return type for x`() {
        val type = compiledFile.typeAtPoint(87)!!

        assertThat(type.toString(), equalTo("Int"))
    }
}
