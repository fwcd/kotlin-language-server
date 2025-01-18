package org.javacs.kt

import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentPositionParams
import org.junit.Test
import org.hamcrest.core.Every.everyItem
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertThat

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

    @Test
    fun `should find members in jdk object`() {
        val result = languageServer.getProtocolExtensionService().overrideMember(TextDocumentPositionParams(TextDocumentIdentifier(fileUri), position(39, 9))).get()

        val titles = result.map { it.title }
        val edits = result.flatMap { it.edit.changes[fileUri]!! }
        val newTexts = edits.map { it.newText }
        val ranges = edits.map { it.range }

        assertThat(titles, hasItems(
            "override fun equals(other: Any?): Boolean { }",
            "override fun hashCode(): Int { }",
            "override fun toString(): String { }",
            "override fun run() { }",
            "override fun clone(): Any { }",
            "override fun start() { }",
            "override fun interrupt() { }",
            "override fun isInterrupted(): Boolean { }",
            "override fun countStackFrames(): Int { }",
            "override fun getContextClassLoader(): ClassLoader { }",
            "override fun setContextClassLoader(p0: ClassLoader) { }",
            "override fun getStackTrace(): (Array<(StackTraceElement..StackTraceElement?)>..Array<out (StackTraceElement..StackTraceElement?)>) { }",
            "override fun getId(): Long { }",
            "override fun getState(): State { }",
            "override fun getUncaughtExceptionHandler(): UncaughtExceptionHandler { }",
        ))

        val padding = System.lineSeparator() + System.lineSeparator() + "    "
        assertThat(newTexts, hasItems(
            padding + "override fun equals(other: Any?): Boolean { }",
            padding + "override fun hashCode(): Int { }",
            padding + "override fun toString(): String { }",
            padding + "override fun run() { }",
            padding + "override fun clone(): Any { }",
            padding + "override fun start() { }",
            padding + "override fun interrupt() { }",
            padding + "override fun isInterrupted(): Boolean { }",
            padding + "override fun countStackFrames(): Int { }",
            padding + "override fun getContextClassLoader(): ClassLoader { }",
            padding + "override fun setContextClassLoader(p0: ClassLoader) { }",
            padding + "override fun getStackTrace(): (Array<(StackTraceElement..StackTraceElement?)>..Array<out (StackTraceElement..StackTraceElement?)>) { }",
            padding + "override fun getId(): Long { }",
            padding + "override fun getState(): State { }",
            padding + "override fun getUncaughtExceptionHandler(): UncaughtExceptionHandler { }",
        ))

        assertThat(ranges, everyItem(equalTo(range(39, 25, 39, 25))))
    }
}
