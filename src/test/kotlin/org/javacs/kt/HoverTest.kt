package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasToString
import org.junit.Assert.assertThat
import org.junit.Test

class HoverTest: TestBase() {

    @Test
    fun `run the Kotlin compiler`() {
        val text = """
fun main(args: Array<String>) {
    println("Hello world!")
}"""
        val (file, _) = parseAnalyze(text)
        val ex = findExpressionAt(file, 40)

        assertThat(ex?.text, equalTo("println"))
        assertThat(parent(ex)?.text, equalTo("""println("Hello world!")"""))
    }

    @Test
    fun `find the types of expressions`() {
        val text = """
fun main(): string {
    val text = ""
    return text
}"""
        val (file, analyze) = parseAnalyze(text)
        val stringLiteral = findExpressionAt(file, 38)!!
        val textDeclaration = findExpressionAt(file, 32)!!
        val textReference = findExpressionAt(file, 53)!!

        assertThat(stringLiteral.text, equalTo("\"\""))
        assertThat(textDeclaration.text, equalTo("""val text = """""))
        assertThat(textReference.text, equalTo("text"))

        val stringLiteralType = analyze.bindingContext.getType(stringLiteral)
        val textDeclarationType = analyze.bindingContext.getType(textDeclaration)
        val textReferenceType = analyze.bindingContext.getType(textReference)

        assertThat(stringLiteralType, hasToString("String"))
        assertThat(textDeclarationType, hasToString("Unit"))
        assertThat(textReferenceType, hasToString("String"))
    }
}