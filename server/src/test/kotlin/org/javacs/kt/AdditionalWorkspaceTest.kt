package org.javacs.kt

import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import org.hamcrest.Matchers.*
import org.junit.Assert.assertThat
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Test

class AdditionalWorkspaceTest : LanguageServerTestFixture("mainWorkspace") {
    val file = "MainWorkspaceFile.kt"

    fun addWorkspaceRoot() {
        val folder = WorkspaceFolder()
        folder.uri = absoluteWorkspaceRoot("additionalWorkspace").toUri().toString()

        val addWorkspace = DidChangeWorkspaceFoldersParams()
        addWorkspace.event = WorkspaceFoldersChangeEvent()
        addWorkspace.event.added = listOf(folder)

        languageServer.workspaceService.didChangeWorkspaceFolders(addWorkspace)
    }

    @Test fun `junit should be on classpath`() {
        addWorkspaceRoot()
        open(file)

        val hover = languageServer.textDocumentService.hover(hoverParams(file, 5, 14)).get()!!
        assertThat(hover.contents.right.value, containsString("fun assertTrue"))
    }

    @Ignore // TODO
    @Test fun `recompile all when classpath changes`() {
        open(file)

        val hover = languageServer.textDocumentService.hover(hoverParams(file, 5, 14)).get()
        assertThat("No hover before JUnit is added to classpath", hover, nullValue())

        addWorkspaceRoot()
        val hoverAgain = languageServer.textDocumentService.hover(hoverParams(file, 5, 14)).get() ?: return fail("No hover")
        assertThat(hoverAgain.contents.right.value, containsString("fun assertTrue"))
    }
}
