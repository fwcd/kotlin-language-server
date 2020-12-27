package org.javacs.kt

import org.hamcrest.Matchers.hasToString
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.javacs.kt.compiler.Compiler
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.After
import org.junit.BeforeClass
import java.nio.file.Files

class CompilerTest {
    val compiler = Compiler(setOf(), setOf())
    val myTestResources = testResourcesRoot().resolve("compiler")
    val file = myTestResources.resolve("FileToEdit.kt")
    val editedText = """
private class FileToEdit {
    val someVal = 1
}"""

    companion object {
        @JvmStatic @BeforeClass fun setupLogger() {
            LOG.connectStdioBackend()
        }
    }

    @Test fun compileFile() {
        val content = Files.readAllLines(file).joinToString("\n")
        val original = compiler.createKtFile(content, file)
        val (context, _) = compiler.compileKtFile(original, listOf(original))
        val psi = original.findElementAt(45)!!
        val kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("String"))
    }

    @Test fun newFile() {
        val original = compiler.createKtFile(editedText, file)
        val (context, _) = compiler.compileKtFile(original, listOf(original))
        val psi = original.findElementAt(46)!!
        val kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("Int"))
    }

    @Test fun editFile() {
        val content = Files.readAllLines(file).joinToString("\n")
        val original = compiler.createKtFile(content, file)
        var (context, _) = compiler.compileKtFile(original, listOf(original))
        var psi = original.findElementAt(46)!!
        var kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("String"))

        val edited = compiler.createKtFile(editedText, file)
        context = compiler.compileKtFile(edited, listOf(edited)).first
        psi = edited.findElementAt(46)!!
        kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("Int"))
    }

    @Test fun editRef() {
        val file1 = testResourcesRoot().resolve("hover/Recover.kt")
        val content = Files.readAllLines(file1).joinToString("\n")
        val original = compiler.createKtFile(content, file1)
        val (context, _) = compiler.compileKtFile(original, listOf(original))
        val function = original.findElementAt(49)!!.parentsWithSelf.filterIsInstance<KtNamedFunction>().first()
        val scope = context.get(BindingContext.LEXICAL_SCOPE, function.bodyExpression)!!
        val recompile = compiler.createKtDeclaration("""private fun singleExpressionFunction() = intFunction()""")
        val (recompileContext, _) = compiler.compileKtExpression(recompile, scope, setOf(original))
        val intFunctionRef = recompile.findElementAt(41)!!.parentsWithSelf.filterIsInstance<KtReferenceExpression>().first()
        val target = recompileContext.get(BindingContext.REFERENCE_TARGET, intFunctionRef)!!

        assertThat(target.name, hasToString("intFunction"))
    }

    @After fun cleanUp() {
        compiler.close()
    }
}
