package org.javacs.kt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HoverTest: LanguageServerTestFixture("hover") {
    @Test
    fun `string reference`() {
        val file = "Literals.kt"
        open(file)

        val hover = languageServer.textDocumentService.hover(position(file, 3, 19)).get()!!
        val contents = hover.contents.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("val stringLiteral: String", contents.value)
        // File has not been edited
        assertFalse("Re-analyzed file", languageServer.textDocumentService.didReAnalyze(workspaceRoot.resolve(file)))
    }

    @Test
    fun `function reference`() {
        val file = "FunctionReference.kt"
        open(file)

        val hover = languageServer.textDocumentService.hover(position(file, 2, 45)).get()!!
        val contents = hover.contents.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("fun isFoo(s: String): Boolean", contents.value)
        // File has not been edited
        assertFalse("Re-analyzed file", languageServer.textDocumentService.didReAnalyze(workspaceRoot.resolve(file)))
    }

    @Test
    fun `object reference`() {
        val file = "ObjectReference.kt"
        open(file)

        val hover = languageServer.textDocumentService.hover(position(file, 2, 7)).get()!!
        val contents = hover.contents.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("object AnObject", contents.value)
        // File has not been edited
        assertFalse("Re-analyzed file", languageServer.textDocumentService.didReAnalyze(workspaceRoot.resolve(file)))
    }

    @Test
    fun `object reference with incomplete method`() {
        val file = "ObjectReference.kt"
        open(file)

        val hover = languageServer.textDocumentService.hover(position(file, 6, 7)).get()!!
        val contents = hover.contents.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("object AnObject", contents.value)
        // File has not been edited
        assertFalse("Re-analyzed file", languageServer.textDocumentService.didReAnalyze(workspaceRoot.resolve(file)))
    }

    @Test
    fun `object reference with method`() {
        val file = "ObjectReference.kt"
        open(file)

        val hover = languageServer.textDocumentService.hover(position(file, 10, 7)).get()!!
        val contents = hover.contents.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("object AnObject", contents.value)
        // File has not been edited
        assertFalse("Re-analyzed file", languageServer.textDocumentService.didReAnalyze(workspaceRoot.resolve(file)))
    }

    @Test
    fun `object method`() {
        val file = "ObjectReference.kt"
        open(file)

        val hover = languageServer.textDocumentService.hover(position(file, 10, 15)).get()!!
        val contents = hover.contents.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("fun doh(): Unit", contents.value)
        // File has not been edited
        assertFalse("Re-analyzed file", languageServer.textDocumentService.didReAnalyze(workspaceRoot.resolve(file)))
    }

    @Test
    fun `incrementally repair a single-expression function`() {
        val file = "Recover.kt"
        open(file)
        replace(file, 2, 9, "\"Foo\"", "intFunction()")

        val hover = languageServer.textDocumentService.hover(position(file, 2, 11)).get()!!
        val contents = hover.contents.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("fun intFunction(): Int", contents.value)
        // Edit is inside function
        assertFalse("Re-analyzed file", languageServer.textDocumentService.didReAnalyze(workspaceRoot.resolve(file)))
    }

    @Test
    fun `incrementally repair a block function`() {
        val file = "Recover.kt"
        open(file)
        replace(file, 5, 13, "\"Foo\"", "intFunction()")

        val hover = languageServer.textDocumentService.hover(position(file, 5, 13)).get()!!
        val contents = hover.contents.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("fun intFunction(): Int", contents.value)
        // Edit is inside function
        assertFalse("Re-analyzed file", languageServer.textDocumentService.didReAnalyze(workspaceRoot.resolve(file)))
    }
}