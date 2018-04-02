package org.javacs.kt

import com.intellij.psi.util.PsiTreeUtil
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.equalTo
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.calls.callUtil.getParentResolvedCall
import org.junit.Assert.assertThat
import org.junit.Test
import java.nio.file.Paths

class ResolveTest {

    private val fooText = """
object Foo {
    fun foo() = "Foo"
}"""
    private val barText = """
object Bar {
    fun bar() = Foo.foo()
}"""
    private val compiler = Compiler(listOf())
    private val fooFile = compiler.openForEditing(Paths.get("Foo.kt"), fooText)
    private val barFile = compiler.openForEditing(Paths.get("Bar.kt"), barText)

    @Test
    fun `resolve across files`() {
        val context = compiler.compileFully(fooFile, barFile)
        val foo = findExpressionAt(barFile, 36)
        val call = foo.getParentResolvedCall(context, false)!!

        assertThat(call.candidateDescriptor.name.asString(), equalTo("foo"))
        assertThat(call.candidateDescriptor.findPsi()!!.containingFile.name, endsWith("Foo.kt"))
        assertThat(call.candidateDescriptor.containingDeclaration.name.asString(), equalTo("Foo"))
    }

    @Test
    fun `resolve across file not part of compilation`() {
        val context = compiler.compileFully(barFile)
        val foo = findExpressionAt(barFile, 36)
        val call = foo.getParentResolvedCall(context, false)!!

        assertThat(call.candidateDescriptor.name.asString(), equalTo("foo"))
        assertThat(call.candidateDescriptor.findPsi()!!.containingFile.name, endsWith("Foo.kt"))
        assertThat(call.candidateDescriptor.containingDeclaration.name.asString(), equalTo("Foo"))
    }

    fun findExpressionAt(file: KtFile, offset: Int): KtExpression? {
        return PsiTreeUtil.getParentOfType(file.findElementAt(offset), KtExpression::class.java)
    }
}