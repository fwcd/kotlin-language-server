package org.javacs.kt

import com.google.gson.Gson
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.junit.Test
import org.hamcrest.core.Every.everyItem
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertThat

// TODO: what should the title be? just the signature? or the name of the member? should we separate between methods and variables?
// easiest is probably just to show the signatures?

class OverrideMemberTest : SingleFileTestFixture("overridemember", "OverrideMembers.kt") {

    val root = testResourcesRoot().resolve(workspaceRoot)
    val fileUri = root.resolve(file).toUri().toString()
    
    @Test
    fun `should show all overrides for class`() {
        val result = languageServer.getProtocolExtensionService().overrideMember(TextDocumentPositionParams(TextDocumentIdentifier(fileUri), position(9, 8))).get()

        val titles = result.map { it.title }
        val edits = result.flatMap { it.edit.changes[fileUri]!! }
        val newTexts = edits.map { it.newText }
        val ranges = edits.map { it.range }

        assertThat(titles, containsInAnyOrder("override val text: String = TODO(\"SET VALUE\")",
                                              "override fun print() { }",
                                              "override fun equals(other: Any?): Boolean { }",
                                              "override fun hashCode(): Int { }",
                                              "override fun toString(): String { }"))

        val padding = System.lineSeparator() + System.lineSeparator() + "    "
        assertThat(newTexts, containsInAnyOrder(padding + "override val text: String = TODO(\"SET VALUE\")",
                                                padding + "override fun print() { }",
                                                padding + "override fun equals(other: Any?): Boolean { }",
                                                padding + "override fun hashCode(): Int { }",
                                                padding + "override fun toString(): String { }"))
        

        assertThat(ranges, everyItem(equalTo(range(9, 31, 9, 31))))
    }

    @Test
    fun `should show one less override for class where one member is already implemented`() {
        val result = languageServer.getProtocolExtensionService().overrideMember(TextDocumentPositionParams(TextDocumentIdentifier(fileUri), position(11, 8))).get()

        val titles = result.map { it.title }
        val edits = result.flatMap { it.edit.changes[fileUri]!! }
        val newTexts = edits.map { it.newText }
        val ranges = edits.map { it.range }

        assertThat(titles, containsInAnyOrder("override fun print() { }",
                                              "override fun equals(other: Any?): Boolean { }",
                                              "override fun hashCode(): Int { }",
                                              "override fun toString(): String { }"))

        val padding = System.lineSeparator() + System.lineSeparator() + "    "
        assertThat(newTexts, containsInAnyOrder(padding + "override fun print() { }",
                                                padding + "override fun equals(other: Any?): Boolean { }",
                                                padding + "override fun hashCode(): Int { }",
                                                padding + "override fun toString(): String { }"))
        
        assertThat(ranges, everyItem(equalTo(range(12, 56, 12, 56))))
    }

    @Test
    fun `should show NO overrides for class where all other alternatives are already implemented`() {
        val result = languageServer.getProtocolExtensionService().overrideMember(TextDocumentPositionParams(TextDocumentIdentifier(fileUri), position(15, 8))).get()
   
        assertThat(result, hasSize(0))
    }

    @Test
    fun `should find method in open class`() {
        val result = languageServer.getProtocolExtensionService().overrideMember(TextDocumentPositionParams(TextDocumentIdentifier(fileUri), position(37, 8))).get()

        val titles = result.map { it.title }
        val edits = result.flatMap { it.edit.changes[fileUri]!! }
        val newTexts = edits.map { it.newText }
        val ranges = edits.map { it.range }

        assertThat(titles, containsInAnyOrder("override fun numOpenDoorsWithName(input: String): Int { }",
                                              "override fun equals(other: Any?): Boolean { }",
                                              "override fun hashCode(): Int { }",
                                              "override fun toString(): String { }"))

        val padding = System.lineSeparator() + System.lineSeparator() + "    "
        assertThat(newTexts, containsInAnyOrder(padding + "override fun numOpenDoorsWithName(input: String): Int { }",
                                                padding + "override fun equals(other: Any?): Boolean { }",
                                                padding + "override fun hashCode(): Int { }",
                                                padding + "override fun toString(): String { }"))
        
        assertThat(ranges, everyItem(equalTo(range(37, 25, 37, 25))))
    }
    
    // TODO: test for kotlin sdk and jdk classes to verify that it works
}
