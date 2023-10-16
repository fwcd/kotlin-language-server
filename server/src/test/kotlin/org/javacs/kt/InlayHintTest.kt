package org.javacs.kt

import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.Position
import org.hamcrest.Matchers.isIn
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test


private fun predicate(pos: Position, label: String) = {
    hint: InlayHint -> hint.position == pos && hint.label.left.contains(label)
}

private fun nPredicateFilter(
    hints: List<InlayHint>,
    predicates: List<(InlayHint) -> Boolean>
): List<InlayHint> =
    hints.filter {
        predicates.any { p -> p(it) }
    }


class InlayHintDeclarationTest : SingleFileTestFixture("inlayhints", "Declarations.kt") {

    private val hints = languageServer.textDocumentService.inlayHint(inlayHintParams(file,  range(0, 0, 0, 0))).get()

    @Test
    fun `lambda declaration hints`() {
        val result = hints.filter {
            it.position == Position(2, 10)
        }
        assertThat(result, hasSize(1))

        val label = result.single().label.left.replaceBefore("(", "")
        val regex = Regex("\\(([^)]+)\\) -> .*")
        assertTrue(label.matches(regex))
    }

    @Test
    fun `destrucuted declaration hints`() {
        val predicates = listOf(
            predicate(Position(17, 10), "Float"),
            predicate(Position(17, 13), "Double"),
        )
        val result = nPredicateFilter(hints, predicates)
        assertThat(result, hasSize(2))
        assertThat(result, everyItem(isIn(hints)))
    }

    @Test
    fun `should not render hint with explicit type`() {
        val result = hints.filter {
            it.label.left.contains("Type")
        }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `generic type hints`() {
        val expected = listOf(Position(5, 13), Position(20, 7))

        val result = hints.filter {
            it.label.left.matches(Regex("Box<([^)]+)>"))
        }.map { it.position }

        assertEquals(result.size, expected.size)
        assertEquals(result.sortedBy { it.line }, expected.sortedBy { it.line })
    }

    @Test
    fun `inferred hint for single-expression function`() {
        val hint = hints.filter {
            it.position == Position(22, 24)
        }
        assertThat(hint, hasSize(1))
        assertThat(hint.single().label.left, containsString("String"))
    }

}

class InlayHintCallableParameterTest : SingleFileTestFixture("inlayhints", "Parameters.kt") {

    private val hints = languageServer.textDocumentService.inlayHint(inlayHintParams(file,  range(0, 0, 0, 0))).get()

    @Test
    fun `class parameter hints`() {
        val predicates = listOf(
            predicate(Position(13, 4), "x"),
            predicate(Position(14, 4), "y"),
            predicate(Position(15, 4), "z"),
        )
        val result = nPredicateFilter(hints, predicates)
        assertThat(result, hasSize(3))
        assertThat(result, everyItem(isIn(hints)))
    }

    @Test
    fun `has one vararg parameter hint`() {
        val varargHintCount = hints.filter {
            it.label.left.contains("ints")
        }.size
        assertThat(varargHintCount, equalTo(1))
    }

    @Test
    fun `mixed parameter types`(){
        val predicates = listOf(
            predicate(Position(17, 14), "d"),
            predicate(Position(17, 19), "p1"),
            predicate(Position(17, 25), "ints"),
        )
        val result = nPredicateFilter(hints, predicates)
        assertThat(result, hasSize(3))
        assertThat(result, everyItem(isIn(hints)))
    }

    @Test
    fun `inferred lambda parameter type`() {
        val hint = hints.filter {
            it.label.left.contains("Int")
        }
        assertThat(hint, hasSize(1))
        assertThat(hint.single().label.left, containsString("Int"))
    }

}

class InlayHintChainedTest : SingleFileTestFixture("inlayhints", "ChainedMethods.kt") {

    private val hints = languageServer.textDocumentService.inlayHint(inlayHintParams(file,  range(0, 0, 0, 0))).get()

    @Test
    fun `chained hints`() {
        val predicates = listOf(
            predicate(Position(17, 34), "List<String>"),
            predicate(Position(18, 26), "List<Int>"),
            predicate(Position(19, 19), "Array<Int>"),
        )
        val result = nPredicateFilter(hints, predicates)

        assertThat(result, hasSize(3))
        assertThat(result, everyItem(isIn(hints)))
    }

    @Test
    fun `generic chained hints`() {
        val predicates = listOf(
            predicate(Position(22, 16), "A<Int>"),
            predicate(Position(23, 8), "B<Int>"),
        )
        val result = nPredicateFilter(hints, predicates)

        assertThat(result, hasSize(2))
        assertThat(result, everyItem(isIn(hints)))
    }

}
