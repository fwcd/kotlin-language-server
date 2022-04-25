package org.javacs.kt

import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertThat
import org.junit.Test

class ImplementAbstractFunctionsQuickFixTest : SingleFileTestFixture("quickfixes", "SomeSubclass.kt") {
    @Test
    fun `gets workspace edit for all abstract methods when none are implemented`() {
        val diagnostic = Diagnostic(range(3, 1, 3, 19), "")
        diagnostic.code = Either.forLeft("ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED")
        val codeActionParams = codeActionParams(file, 3, 1, 3, 19, listOf(diagnostic), listOf(CodeActionKind.QuickFix))

        val codeActions = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActions.size, equalTo(1))
        assertThat(codeActions[0].right.kind, equalTo(CodeActionKind.QuickFix))
        assertThat(codeActions[0].right.diagnostics.size, equalTo(1))
        assertThat(codeActions[0].right.diagnostics[0], equalTo(diagnostic))
        assertThat(codeActions[0].right.edit.changes.size, equalTo(1))
        assertThat(codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.size, equalTo(2))
        assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.range,
            equalTo(range(3, 55, 3, 55))
        )
        assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.newText,
            equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun someSuperMethod(someParameter: String): Int { }")
        )
        assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(1)?.range,
            equalTo(range(3, 55, 3, 55))
        )
        assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(1)?.newText,
            equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun someInterfaceMethod() { }")
        )
    }

    @Test
    fun `gets workspace edit for interface methods when super class abstract methods are implemented`() {
        val diagnostic = Diagnostic(range(6, 1, 6, 24), "")
        diagnostic.code = Either.forLeft("ABSTRACT_MEMBER_NOT_IMPLEMENTED")
        val codeActionParams = codeActionParams(file, 6, 1, 6, 24, listOf(diagnostic), listOf(CodeActionKind.QuickFix))

        val codeActions = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActions.size, equalTo(1))
        assertThat(codeActions[0].right.kind, equalTo(CodeActionKind.QuickFix))
        assertThat(codeActions[0].right.diagnostics.size, equalTo(1))
        assertThat(codeActions[0].right.diagnostics[0], equalTo(diagnostic))
        assertThat(codeActions[0].right.edit.changes.size, equalTo(1))
        assertThat(codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.size, equalTo(1))
        assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.range,
            equalTo(range(7, 74, 7, 74))
        )
        assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.newText,
            equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun someInterfaceMethod() { }")
        )
    }

    @Test
    fun `gets workspace edit for super class abstract methods when interface methods are implemented`() {
        val diagnostic = Diagnostic(range(10, 1, 10, 25), "")
        diagnostic.code = Either.forLeft("ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED")
        val codeActionParams = codeActionParams(file, 10, 1, 10, 25, listOf(diagnostic), listOf(CodeActionKind.QuickFix))

        val codeActions = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActions.size, equalTo(1))
        assertThat(codeActions[0].right.kind, equalTo(CodeActionKind.QuickFix))
        assertThat(codeActions[0].right.diagnostics.size, equalTo(1))
        assertThat(codeActions[0].right.diagnostics[0], equalTo(diagnostic))
        assertThat(codeActions[0].right.edit.changes.size, equalTo(1))
        assertThat(codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.size, equalTo(1))
        assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.range,
            equalTo(range(11, 43, 11, 43))
        )
        assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.newText,
            equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun someSuperMethod(someParameter: String): Int { }")
        )
    }
}

class ImplementAbstractFunctionsQuickFixSameFileTest : SingleFileTestFixture("quickfixes", "samefile.kt") {
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
        assertThat(functionToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun test(input: String, otherInput: Int) { }"))
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

        val textEdit = codeAction.edit.changes
        val key = workspaceRoot.resolve(file).toUri().toString()
        assertThat(textEdit.containsKey(key), equalTo(true))
        assertThat(textEdit[key], hasSize(2))

        val firstFunctionToImplementEdit = textEdit[key]?.get(0)
        assertThat(firstFunctionToImplementEdit?.range, equalTo(range(15, 49, 15, 49)))
        assertThat(firstFunctionToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun print() { }"))
        
        val secondFunctionToImplementEdit = textEdit[key]?.get(1)
        assertThat(secondFunctionToImplementEdit?.range, equalTo(range(15, 49, 15, 49)))
        assertThat(secondFunctionToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun test(input: String, otherInput: Int) { }"))
    }

    @Test
    fun `should find only one abstract method when the other one is already implemented`() {
        val only = listOf(CodeActionKind.QuickFix)
        val codeActionParams = codeActionParams(file, 17, 1, 17, 26, diagnostics, only)
    
        val codeActionResult = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActionResult, hasSize(1))
        val codeAction = codeActionResult[0].right
        assertThat(codeAction.kind, equalTo(CodeActionKind.QuickFix))
        assertThat(codeAction.title, equalTo("Implement abstract functions"))

        val textEdit = codeAction.edit.changes
        val key = workspaceRoot.resolve(file).toUri().toString()
        assertThat(textEdit.containsKey(key), equalTo(true))
        assertThat(textEdit[key], hasSize(1))
        
        val functionToImplementEdit = textEdit[key]?.get(0)
        assertThat(functionToImplementEdit?.range, equalTo(range(18, 57, 18, 57)))
        assertThat(functionToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun print() { }"))
    }

    @Test
    fun `should respect nullability of parameter and return value in abstract method`() {
        val only = listOf(CodeActionKind.QuickFix)
        val codeActionParams = codeActionParams(file, 25, 1, 25, 16, diagnostics, only)
    
        val codeActionResult = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActionResult, hasSize(1))
        val codeAction = codeActionResult[0].right
        assertThat(codeAction.kind, equalTo(CodeActionKind.QuickFix))
        assertThat(codeAction.title, equalTo("Implement abstract functions"))

        val textEdit = codeAction.edit.changes
        val key = workspaceRoot.resolve(file).toUri().toString()
        assertThat(textEdit.containsKey(key), equalTo(true))
        assertThat(textEdit[key], hasSize(1))

        val functionToImplementEdit = textEdit[key]?.get(0)
        assertThat(functionToImplementEdit?.range, equalTo(range(25, 48, 25, 48)))
        assertThat(functionToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun myMethod(myStr: String?): String? { }"))
    }
}

class ImplementAbstractFunctionsQuickFixExternalLibraryTest : SingleFileTestFixture("quickfixes", "standardlib.kt") {
    @Test
    fun `should find one abstract method from Runnable to implement`() {
        val only = listOf(CodeActionKind.QuickFix)
        val codeActionParams = codeActionParams(file, 5, 1, 5, 15, diagnostics, only)
    
        val codeActionResult = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActionResult, hasSize(1))
        val codeAction = codeActionResult[0].right
        assertThat(codeAction.kind, equalTo(CodeActionKind.QuickFix))
        assertThat(codeAction.title, equalTo("Implement abstract functions"))

        val textEdit = codeAction.edit.changes
        val key = workspaceRoot.resolve(file).toUri().toString()
        assertThat(textEdit.containsKey(key), equalTo(true))
        assertThat(textEdit[key], hasSize(1))

        val functionToImplementEdit = textEdit[key]?.get(0)
        assertThat(functionToImplementEdit?.range, equalTo(range(5, 28, 5, 28)))
        assertThat(functionToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun run() { }"))
    }

    @Test
    fun `should find one abstract method from Comparable to implement`() {
        val only = listOf(CodeActionKind.QuickFix)
        val codeActionParams = codeActionParams(file, 7, 1, 7, 19, diagnostics, only)
    
        val codeActionResult = languageServer.textDocumentService.codeAction(codeActionParams).get()

        assertThat(codeActionResult, hasSize(1))
        val codeAction = codeActionResult[0].right
        assertThat(codeAction.kind, equalTo(CodeActionKind.QuickFix))
        assertThat(codeAction.title, equalTo("Implement abstract functions"))

        val textEdit = codeAction.edit.changes
        val key = workspaceRoot.resolve(file).toUri().toString()
        assertThat(textEdit.containsKey(key), equalTo(true))
        assertThat(textEdit[key], hasSize(1))

        val functionToImplementEdit = textEdit[key]?.get(0)
        assertThat(functionToImplementEdit?.range, equalTo(range(7, 42, 7, 42)))
        assertThat(functionToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun compare(p0: String, p1: String): Int { }"))
    }
}
