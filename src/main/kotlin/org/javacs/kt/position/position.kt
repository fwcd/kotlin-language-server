package org.javacs.kt.position

import com.intellij.openapi.util.TextRange
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

/**
 * Convert from 0-based line and column to 0-based offset
 */
fun offset(content: String, line: Int, char: Int): Int {
    val reader = content.reader()
    var offset = 0

    var lineOffset = 0
    while (lineOffset < line) {
        val nextChar = reader.read()

        if (nextChar == -1)
            throw RuntimeException("Reached end of file before reaching line $line")

        if (nextChar.toChar() == '\n')
            lineOffset++

        offset++
    }

    var charOffset = 0
    while (charOffset < char) {
        val nextChar = reader.read()

        if (nextChar == -1)
            throw RuntimeException("Reached end of file before reaching char $char")

        charOffset++
        offset++
    }

    return offset
}

fun position(content: String, offset: Int): Position {
    val reader = content.reader()
    var line = 0
    var char = 0

    var find = 0
    while (find < offset) {
        val nextChar = reader.read()

        if (nextChar == -1)
            throw RuntimeException("Reached end of file before reaching offset $offset")

        find++
        char++

        if (nextChar.toChar() == '\n') {
            line++
            char = 0
        }
    }

    return Position(line, char)
}

fun range(content: String, range: TextRange) =
        Range(position(content, range.startOffset), position(content, range.endOffset))