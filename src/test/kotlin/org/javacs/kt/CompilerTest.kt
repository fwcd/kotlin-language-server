package org.javacs.kt

import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasToString
import org.javacs.kt.util.findParent
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode.LocalDeclarations
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode.TopLevelDeclarations
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.junit.Assert.assertThat
import org.junit.Test
import java.nio.file.Files

class CompilerTest {
    val compiler = Compiler(setOf())
    val myTestResources = testResourcesRoot().resolve("compiler")
    val file = myTestResources.resolve("FileToEdit.kt")
    val editedText = """
private class FileToEdit {
    val someVal = 1
}"""

    @Test fun compileFile() {
        val content = Files.readAllLines(file).joinToString("\n")
        val original = compiler.createFile(file, content)
        val (context, _) = compiler.compileFile(original, listOf(original))
        val psi = original.findElementAt(45)!!
        val kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("String"))
    }

    @Test fun newFile() {
        val original = compiler.createFile(file, editedText)
        val (context, _) = compiler.compileFile(original, listOf(original))
        val psi = original.findElementAt(46)!!
        val kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("Int"))
    }

    @Test fun editFile() {
        val content = Files.readAllLines(file).joinToString("\n")
        val original = compiler.createFile(file, content)
        var (context, _) = compiler.compileFile(original, listOf(original))
        var psi = original.findElementAt(46)!!
        var kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("String"))

        val edited = compiler.createFile(file, editedText)
        context = compiler.compileFile(edited, listOf(edited)).first
        psi = edited.findElementAt(46)!!
        kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("Int"))
    }

    @Test fun editRef() {
        val file1 = testResourcesRoot().resolve("hover/Recover.kt")
        val content = Files.readAllLines(file1).joinToString("\n")
        val original = compiler.createFile(file1, content)
        val (context, _) = compiler.compileFile(original, listOf(original))
        val function = original.findElementAt(49)!!.parentsWithSelf.filterIsInstance<KtNamedFunction>().first()
        val scope = context.get(BindingContext.LEXICAL_SCOPE, function.bodyExpression)!!
        val recompile = compiler.createDeclaration("""private fun singleExpressionFunction() = intFunction()""")
        val (recompileContext, _) = compiler.compileExpression(recompile, scope, setOf(original))
        val intFunctionRef = recompile.findElementAt(41)!!.parentsWithSelf.filterIsInstance<KtReferenceExpression>().first()
        val target = recompileContext.get(BindingContext.REFERENCE_TARGET, intFunctionRef)!!

        assertThat(target.name, hasToString("intFunction"))
    }
}