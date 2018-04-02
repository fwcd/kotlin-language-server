package org.javacs.kt

import org.hamcrest.Matchers.hasToString
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.junit.Assert.assertThat
import org.junit.Test

class CompilerTest {
    val myTestResources = testResourcesRoot().resolve("compiler")
    val file = myTestResources.resolve("FileToEdit.kt")
    val editedText = """
private class FileToEdit {
    val someVal = 1
}"""

    @Test
    fun compileFile() {
        val original = Compiler.openFile(file)
        val compiler = Compiler(listOf())
        val context = compiler.compileFully(original)
        val psi = original.findElementAt(45)!!
        val kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("String"))
    }

    @Test
    fun newFile() {
        val compiler = Compiler(listOf(Compiler.openFile(file)))
        val original = compiler.openForEditing(file, editedText)
        val context = compiler.compileFully(original)
        val psi = original.findElementAt(46)!!
        val kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("Int"))
    }

    @Test
    fun editFile() {
        val original = Compiler.openFile(file)
        val compiler = Compiler(listOf(original))
        var context = compiler.compileFully(original)
        var psi = original.findElementAt(46)!!
        var kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("String"))

        val edited = compiler.openForEditing(file, editedText)
//        val documentManager = PsiDocumentManager.getInstance(original.project)
//        val document = documentManager.getDocument(original)!!
//        document.setText(editedText)
//        original.replace(edited)
//        val newFile = LightVirtualFile(path.toAbsolutePath().toString().substring(1), editedText)
//        val newView = SingleRootFileViewProvider(original.manager, newFile)
//        val edited = KtFile(newView, false)
//        assertNotNull(newView.virtualFile)
//        assertTrue(newView.isEventSystemEnabled)
//        assertNotNull(edited.virtualFile)
        context = compiler.compileFully(edited)
        psi = edited.findElementAt(46)!!
        kt = psi.parentsWithSelf.filterIsInstance<KtExpression>().first()

        assertThat(context.getType(kt), hasToString("Int"))
    }
}