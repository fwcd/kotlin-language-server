package org.javacs.kt

import org.junit.Assert.assertEquals
import org.junit.Test

class HoverLiteralsTest: SingleFileTestFixture("hover", "Literals.kt") {
    @Test fun `string reference`() {
        val hover = languageServer.textDocumentService.hover(textDocumentPosition(file, 3, 19)).get()!!
        val contents = hover.contents.left.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("val stringLiteral: String", contents.value)
    }
}

class HoverFunctionReferenceTest: SingleFileTestFixture("hover", "FunctionReference.kt") {
    @Test fun `function reference`() {
        val hover = languageServer.textDocumentService.hover(textDocumentPosition(file, 2, 45)).get()!!
        val contents = hover.contents.left.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("fun isFoo(s: String): Boolean", contents.value)
    }
}

class HoverObjectReferenceTest: SingleFileTestFixture("hover", "ObjectReference.kt") {
    @Test fun `object reference`() {
        val hover = languageServer.textDocumentService.hover(textDocumentPosition(file, 2, 7)).get()!!
        val contents = hover.contents.left.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("object AnObject", contents.value)
    }

    @Test fun `object reference with incomplete method`() {
        val hover = languageServer.textDocumentService.hover(textDocumentPosition(file, 6, 7)).get()!!
        val contents = hover.contents.left.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("object AnObject", contents.value)
    }

    @Test fun `object reference with method`() {
        val hover = languageServer.textDocumentService.hover(textDocumentPosition(file, 10, 7)).get()!!
        val contents = hover.contents.left.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("object AnObject", contents.value)
    }

    @Test fun `object method`() {
        val hover = languageServer.textDocumentService.hover(textDocumentPosition(file, 10, 15)).get()!!
        val contents = hover.contents.left.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("fun doh(): Unit", contents.value)
    }
}

class HoverRecoverTest: SingleFileTestFixture("hover", "Recover.kt") {
    @Test fun `incrementally repair a single-expression function`() {
        replace(file, 2, 9, "\"Foo\"", "intFunction()")

        val hover = languageServer.textDocumentService.hover(textDocumentPosition(file, 2, 11)).get()!!
        val contents = hover.contents.left.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("fun intFunction(): Int", contents.value)
    }

    @Test fun `incrementally repair a block function`() {
        replace(file, 5, 13, "\"Foo\"", "intFunction()")

        val hover = languageServer.textDocumentService.hover(textDocumentPosition(file, 5, 13)).get()!!
        val contents = hover.contents.left.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("fun intFunction(): Int", contents.value)
    }
}

class HoverAcrossFilesTest: LanguageServerTestFixture("hover") {
    @Test fun `resolve across files`() {
        val from = "ResolveFromFile.kt"
        val to = "ResolveToFile.kt"
        open(from)
        open(to)

        val hover = languageServer.textDocumentService.hover(textDocumentPosition(from, 3, 26)).get()!!
        val contents = hover.contents.left.first().right

        assertEquals("kotlin", contents.language)
        assertEquals("fun target(): Unit", contents.value)
    }
}