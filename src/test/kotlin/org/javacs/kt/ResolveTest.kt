package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.resolve.calls.callUtil.getParentResolvedCall
import org.junit.Assert.assertThat
import org.junit.Test

class ResolveTest: TestBase() {
    @Test
    fun `resolve a function defined in the same file`() {
        val text = """
fun foo() = "Foo"
fun main() = foo()
}"""
        val (file, analyze) = parseAnalyze(text)
        val foo = findExpressionAt(file, 34)!!
        val call = foo.getParentResolvedCall(analyze.bindingContext, false)!!

        assertThat(call.candidateDescriptor.name.asString(), equalTo("foo"))
        assertThat(call.candidateDescriptor.findPsi()!!.containingFile.name, equalTo(testFileName))
    }
}