package org.javacs.kt

import org.eclipse.lsp4j.*
import org.javacs.kt.SingleFileTestFixture
import org.junit.Test
import org.junit.Assert.assertThat
import org.junit.Assert.fail
import org.hamcrest.Matchers.*

// TODO: naming
class ImplementAbstractMembersQuickFixSameFileTest : SingleFileTestFixture("codeactions", "samefile.kt") {

    @Test
    fun `should find no code actions`() {
        val only = listOf(CodeActionKind.QuickFix)
        val codeActionParams = codeActionParams(file, 3, 1, 3, 22, diagnostics, only)
    
        val codeActionResult = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActionResult, hasSize(0))
    }

    @Test
    fun `should find one abstract method to implement`() {
        val only = listOf(CodeActionKind.QuickFix)
        val codeActionParams = codeActionParams(file, 7, 1, 7, 14, diagnostics, only)
    
        val codeActionResult = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActionResult, hasSize(1))
        val codeAction = codeActionResult[0].right
        assertThat(codeAction.kind, equalTo(CodeActionKind.QuickFix))
        assertThat(codeAction.title, equalTo("Implement abstract functions"))
        assertThat(codeAction.diagnostics, equalTo(listOf(diagnostics[0])))

        val textEdit = codeAction.edit.changes
        val key = workspaceRoot.resolve(file).toUri().toString()
        assertThat(textEdit.containsKey(key), equalTo(true))
        assertThat(textEdit[key], hasSize(1))

        val functionToImplementEdit = textEdit[key]?.get(0)
        assertThat(functionToImplementEdit?.range, equalTo(range(7, 30, 7, 30)))
        assertThat(functionToImplementEdit?.newText, equalTo("\n\n    override fun test(input: String, otherInput: Int) { }"))
    }

    @Test
    fun `should find several abstract methods to implement`() {
        val only = listOf(CodeActionKind.QuickFix)
        val codeActionParams = codeActionParams(file, 15, 1, 15, 21, diagnostics, only)
    
        val codeActionResult = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActionResult, hasSize(1))
        val codeAction = codeActionResult[0].right
        assertThat(codeAction.kind, equalTo(CodeActionKind.QuickFix))
        assertThat(codeAction.title, equalTo("Implement abstract functions"))
        //assertThat(codeAction.diagnostics, equalTo(listOf(diagnostics[0])))

        val textEdit = codeAction.edit.changes
        val key = workspaceRoot.resolve(file).toUri().toString()
        assertThat(textEdit.containsKey(key), equalTo(true))
        assertThat(textEdit[key], hasSize(2))

        val firstFunctionToImplementEdit = textEdit[key]?.get(0)
        assertThat(firstFunctionToImplementEdit?.range, equalTo(range(15, 49, 15, 49)))
        assertThat(firstFunctionToImplementEdit?.newText, equalTo("\n\n    override fun print() { }"))
        
        val secondFunctionToImplementEdit = textEdit[key]?.get(1)
        assertThat(secondFunctionToImplementEdit?.range, equalTo(range(15, 49, 15, 49)))
        assertThat(secondFunctionToImplementEdit?.newText, equalTo("\n\n    override fun test(input: String, otherInput: Int) { }"))
    }
}



