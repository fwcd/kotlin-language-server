package org.javacs.kt

import org.eclipse.lsp4j.Position
import org.hamcrest.Matchers.startsWith
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class RenameReferenceTest : SingleFileTestFixture("rename", "SomeClass.kt") {

    @Test
    fun `rename in the reference`() {
        val edits = languageServer.textDocumentService.rename(renameParams(file, 4, 26, "NewClassName")).get()!!
        val changes = edits.documentChanges

        assertThat(changes.size, equalTo(3))
        assertThat(changes[0].left.textDocument.uri, startsWith("file://"))
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
        assertThat(changes[0].left.textDocument.uri, startsWith("file://"))
        assertThat(changes[0].left.textDocument.uri, containsString("SomeOtherClass.kt"))

        assertThat(changes[0].left.edits[0].newText, equalTo("NewClassName"))
        assertThat(changes[0].left.edits[0].range.start, equalTo(Position(2, 6)))
        assertThat(changes[0].left.edits[0].range.end, equalTo(Position(2, 20)))
    }
}

class RenameDeclarationSiteTest : SingleFileTestFixture("rename", "DeclSite.kt") {

    @Test
    fun `should rename variable from usage site`() {
        val usageFile = workspaceRoot.resolve("UsageSite.kt").toString()
        val edits = languageServer.textDocumentService.rename(renameParams(usageFile, 4, 13, "newvarname")).get()!!
        val changes = edits.documentChanges

        assertThat(changes.size, equalTo(2))

        val firstChange = changes[0].left
        assertThat(firstChange.textDocument.uri, startsWith("file://"))
        assertThat(firstChange.textDocument.uri, containsString("DeclSite.kt"))
        assertThat(firstChange.edits[0].newText, equalTo("newvarname"))
        assertThat(firstChange.edits[0].range, equalTo(range(3, 5, 3, 10)))

        val secondChange = changes[1].left
        assertThat(secondChange.textDocument.uri, startsWith("file://"))
        assertThat(secondChange.textDocument.uri, containsString("UsageSite.kt"))
        assertThat(secondChange.edits[0].newText, equalTo("newvarname"))
        assertThat(secondChange.edits[0].range, equalTo(range(4, 13, 4, 18)))
    }

    @Test
    fun `should rename variable from declaration site`() {
        val edits = languageServer.textDocumentService.rename(renameParams(file, 3, 6, "newvarname")).get()!!
        val changes = edits.documentChanges

        assertThat(changes.size, equalTo(2))

        val firstChange = changes[0].left
        assertThat(firstChange.textDocument.uri, startsWith("file://"))
        assertThat(firstChange.textDocument.uri, containsString("DeclSite.kt"))
        assertThat(firstChange.edits[0].newText, equalTo("newvarname"))
        assertThat(firstChange.edits[0].range, equalTo(range(3, 5, 3, 10)))

        val secondChange = changes[1].left
        assertThat(secondChange.textDocument.uri, startsWith("file://"))
        assertThat(secondChange.textDocument.uri, containsString("UsageSite.kt"))
        assertThat(secondChange.edits[0].newText, equalTo("newvarname"))
        assertThat(secondChange.edits[0].range, equalTo(range(4, 13, 4, 18)))
    }
}
