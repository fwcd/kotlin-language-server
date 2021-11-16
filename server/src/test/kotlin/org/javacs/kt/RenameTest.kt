package org.javacs.kt

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.hasProperty
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertThat
import org.junit.Test

class RenameReferenceTest : SingleFileTestFixture("rename", "SomeClass.kt") {

    @Test
    fun `rename in the reference`() {
        val edits = languageServer.textDocumentService.rename(renameParams(file, 4, 26, "NewClassName")).get()!!
        val changes = edits.documentChanges

        assertThat(changes.size, equalTo(3))
        assertThat(changes[0].left.textDocument.uri, containsString("SomeOtherClass.kt"))

        assertThat(changes[0].left.edits[0].newText, equalTo("NewClassName"))
        assertThat(changes[0].left.edits[0].range.start, equalTo(Position(2, 6)))
        assertThat(changes[0].left.edits[0].range.end, equalTo(Position(2, 20)))
    }
}

class RenameDefinitionTest : SingleFileTestFixture("rename", "SomeOtherClass.kt") {

    @Test
    fun `rename in the definition`() {
        val edits = languageServer.textDocumentService.rename(renameParams(file, 2, 15, "NewClassName")).get()!!
        val changes = edits.documentChanges

        println(changes)

        assertThat(changes.size, equalTo(3))
        assertThat(changes[0].left.textDocument.uri, containsString("SomeOtherClass.kt"))

        assertThat(changes[0].left.edits[0].newText, equalTo("NewClassName"))
        assertThat(changes[0].left.edits[0].range.start, equalTo(Position(2, 6)))
        assertThat(changes[0].left.edits[0].range.end, equalTo(Position(2, 20)))
    }
}
