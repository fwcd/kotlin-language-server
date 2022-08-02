package org.javacs.kt

import com.google.gson.Gson
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.junit.Test
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertThat

// TODO: what should the title be? just the signature? or the name of the member? should we separate between methods and variables?

class OverrideMemberTest : SingleFileTestFixture("overridemember", "OverrideMembers.kt") {

    val root = testResourcesRoot().resolve(workspaceRoot)
    val fileUri = root.resolve(file).toUri().toString()
    
    @Test
    fun `should show all overrides for class`() {
        val result = languageServer.getProtocolExtensionService().overrideMember(TextDocumentPositionParams(TextDocumentIdentifier(fileUri), position(9, 8))).get()
   
        assertThat(result, hasSize(2))
        
        val firstCodeAction = result[0]
        assertThat(firstCodeAction.title, equalTo("text"))

        val firstTextEdit = firstCodeAction.edit.changes
        assertThat(firstTextEdit.containsKey(fileUri), equalTo(true))
        assertThat(firstTextEdit[fileUri], hasSize(1))
        
        val memberToImplementEdit = firstTextEdit[fileUri]?.get(0)
        assertThat(memberToImplementEdit?.range, equalTo(range(9, 32, 9, 32)))
        assertThat(memberToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override val text: String = ???"))


        val secondCodeAction = result[1]
        assertThat(secondCodeAction.title, equalTo("print"))

        val secondTextEdit = secondCodeAction.edit.changes
        assertThat(secondTextEdit.containsKey(fileUri), equalTo(true))
        assertThat(secondTextEdit[fileUri], hasSize(1))
        
        val functionToImplementEdit = secondTextEdit[fileUri]?.get(0)
        assertThat(functionToImplementEdit?.range, equalTo(range(9, 32, 9, 32)))
        assertThat(functionToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun print() {}"))
    }

    @Test
    fun `should show one override for class where other alternatives are already implemented`() {
        val result = languageServer.getProtocolExtensionService().overrideMember(TextDocumentPositionParams(TextDocumentIdentifier(fileUri), position(11, 8))).get()
   
        assertThat(result, hasSize(1))
      
        val codeAction = result[0]
        assertThat(codeAction.title, equalTo("print"))

        val textEdit = codeAction.edit.changes
        assertThat(textEdit.containsKey(fileUri), equalTo(true))
        assertThat(textEdit[fileUri], hasSize(1)) 
        
        val functionToImplementEdit = textEdit[fileUri]?.get(0)
        assertThat(functionToImplementEdit?.range, equalTo(range(12, 57, 12, 57)))
        assertThat(functionToImplementEdit?.newText, equalTo(System.lineSeparator() + System.lineSeparator() + "    override fun print() {}"))
    }

    @Test
    fun `should show NO overrides for class where all other alternatives are already implemented`() {
        val result = languageServer.getProtocolExtensionService().overrideMember(TextDocumentPositionParams(TextDocumentIdentifier(fileUri), position(15, 8))).get()
   
        assertThat(result, hasSize(0))
    }

    // TODO: test for kotlin sdk and jdk classes to verify that it works
}
