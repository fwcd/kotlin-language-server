package org.javacs.kt

import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Test

class ImplementAbstractFunctionsQuickFixTest : SingleFileTestFixture("quickfixes", "SomeSubclass.kt") {
    @Test
    fun `gets workspace edit for all abstract methods when none are implemented`() {
        val diagnostic = Diagnostic(range(3, 1, 3, 19), "")
        diagnostic.code = Either.forLeft("ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED")
        val codeActionParams = codeActionParams(file, 3, 1, 3, 19, listOf(diagnostic), listOf(CodeActionKind.QuickFix))

        val codeActions = languageServer.textDocumentService.codeAction(codeActionParams).get()

        Assert.assertThat(codeActions.size, Matchers.equalTo(1))
        Assert.assertThat(codeActions[0].right.kind, Matchers.equalTo(CodeActionKind.QuickFix))
        Assert.assertThat(codeActions[0].right.diagnostics.size, Matchers.equalTo(1))
        Assert.assertThat(codeActions[0].right.diagnostics[0], Matchers.equalTo(diagnostic))
        Assert.assertThat(codeActions[0].right.edit.changes.size, Matchers.equalTo(1))
        Assert.assertThat(codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.size, Matchers.equalTo(2))
        Assert.assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.range,
            Matchers.equalTo(range(3, 55, 3, 55))
        )
        Assert.assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.newText,
            Matchers.equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun someSuperMethod(someParameter: String): Int { }")
        )
        Assert.assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(1)?.range,
            Matchers.equalTo(range(3, 55, 3, 55))
        )
        Assert.assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(1)?.newText,
            Matchers.equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun someInterfaceMethod() { }")
        )
    }

    @Test
    fun `gets workspace edit for interface methods when super class abstract methods are implemented`() {
        val diagnostic = Diagnostic(range(6, 1, 6, 24), "")
        diagnostic.code = Either.forLeft("ABSTRACT_MEMBER_NOT_IMPLEMENTED")
        val codeActionParams = codeActionParams(file, 6, 1, 6, 24, listOf(diagnostic), listOf(CodeActionKind.QuickFix))

        val codeActions = languageServer.textDocumentService.codeAction(codeActionParams).get()

        Assert.assertThat(codeActions.size, Matchers.equalTo(1))
        Assert.assertThat(codeActions[0].right.kind, Matchers.equalTo(CodeActionKind.QuickFix))
        Assert.assertThat(codeActions[0].right.diagnostics.size, Matchers.equalTo(1))
        Assert.assertThat(codeActions[0].right.diagnostics[0], Matchers.equalTo(diagnostic))
        Assert.assertThat(codeActions[0].right.edit.changes.size, Matchers.equalTo(1))
        Assert.assertThat(codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.size, Matchers.equalTo(1))
        Assert.assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.range,
            Matchers.equalTo(range(7, 74, 7, 74))
        )
        Assert.assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.newText,
            Matchers.equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun someInterfaceMethod() { }")
        )
    }

    @Test
    fun `gets workspace edit for super class abstract methods when interface methods are implemented`() {
        val diagnostic = Diagnostic(range(10, 1, 10, 25), "")
        diagnostic.code = Either.forLeft("ABSTRACT_CLASS_MEMBER_NOT_IMPLEMENTED")
        val codeActionParams = codeActionParams(file, 10, 1, 10, 25, listOf(diagnostic), listOf(CodeActionKind.QuickFix))

        val codeActions = languageServer.textDocumentService.codeAction(codeActionParams).get()

        Assert.assertThat(codeActions.size, Matchers.equalTo(1))
        Assert.assertThat(codeActions[0].right.kind, Matchers.equalTo(CodeActionKind.QuickFix))
        Assert.assertThat(codeActions[0].right.diagnostics.size, Matchers.equalTo(1))
        Assert.assertThat(codeActions[0].right.diagnostics[0], Matchers.equalTo(diagnostic))
        Assert.assertThat(codeActions[0].right.edit.changes.size, Matchers.equalTo(1))
        Assert.assertThat(codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.size, Matchers.equalTo(1))
        Assert.assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.range,
            Matchers.equalTo(range(11, 43, 11, 43))
        )
        Assert.assertThat(
            codeActions[0].right.edit.changes[codeActionParams.textDocument.uri]?.get(0)?.newText,
            Matchers.equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun someSuperMethod(someParameter: String): Int { }")
        )
    }
}
