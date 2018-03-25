package org.javacs.kt

import org.hamcrest.Matchers.*
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.resolve.calls.callUtil.getParentCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getParentResolvedCall
import org.jetbrains.kotlin.resolve.scopes.receivers.QualifierReceiver
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

    @Test
    fun `resolve a member defined in the same file`() {
        val text = """
object Foo {
    fun foo() = "Foo"
}
object Bar {
    fun bar() = Foo.foo() + "Bar"
}"""
        val (file, analyze) = parseAnalyze(text)
        val foo = findExpressionAt(file, 73)!!
        val call = foo.getParentResolvedCall(analyze.bindingContext, false)!!

        assertThat(call.candidateDescriptor.name.asString(), equalTo("foo"))
        assertThat(call.candidateDescriptor.findPsi()!!.containingFile.name, equalTo(testFileName))
        assertThat(call.candidateDescriptor.containingDeclaration.name.asString(), equalTo("Foo"))
    }

    @Test
    fun `resolve an incomplete member`() {
        val text = """
object Foo {
    fun foo() = "Foo"
}
object Bar {
    fun bar() = Foo.fo
}"""
        val (file, analyze) = parseAnalyze(text)
        val foo = findExpressionAt(file, 72)!!
        val call = foo.getParentCall(analyze.bindingContext, false)!!
        val receiver = call.explicitReceiver as QualifierReceiver

        assertThat(receiver.staticScope.getFunctionNames(), hasItem(hasToString("foo")))
    }
}