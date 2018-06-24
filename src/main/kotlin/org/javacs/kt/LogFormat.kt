package org.javacs.kt

import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import java.util.logging.Formatter
import java.util.logging.LogRecord
import java.util.logging.Logger

class LogFormat: Formatter() {
    private val logTime = false
    private var newline = System.lineSeparator()
    private val date = Date()
    private var maxSource = 0
    private var maxThread = 0

    override fun format(record: LogRecord): String {
        date.time = record.millis
        val time = if (logTime) "$date " else ""

        var source: String
        if (record.sourceClassName != null) {
            source = record.sourceClassName.split(".").last()
            if (record.sourceMethodName != null) {
                source += "." + record.sourceMethodName
            }
        } else {
            source = record.loggerName
        }

        val message = formatMessage(record)
        var throwable = ""
        if (record.thrown != null) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println()
            record.thrown.printStackTrace(pw)
            pw.close()
            throwable = sw.toString() + newline
        }

        var thread = Thread.currentThread().name
        val prefix = "$time[${record.level.localizedName}]"

        return multiLineFormat(
            2, // padding between columns
            FormatValue(prefix, 10),
            FormatValue(shortenOrPad(thread, 10)),
            FormatValue(source, 45),
            FormatValue(message)
        ) + throwable
    }

    data class FormatValue(val str: String, val charsPerLine: Int = str.length)

    private fun multiLineFormat(padding: Int, vararg values: FormatValue): String {
        val splittedValues = values.map { createLineBreaks(it.str, it.charsPerLine) }
        return mergeSplittedLines(splittedValues, padding)
    }

    private fun mergeSplittedLines(splittedValues: List<List<String>>, padding: Int): String {
        var charOffset = 0
        val lines = mutableListOf<String>()
        for (splittedValue in splittedValues) {
            var lineIndex = 0
            var maxOffset = 0
            for (valueLine in splittedValue) {
                while (lineIndex >= lines.size) lines.add("")

                lines[lineIndex] = lines[lineIndex].padEnd(charOffset, ' ') + valueLine

                maxOffset = Math.max(maxOffset, valueLine.length)
                lineIndex++
            }
            charOffset += maxOffset + padding
        }
        return lines.reduce { prev, current -> prev + newline + current } + newline
    }

    private fun createLineBreaks(str: String, maxLength: Int): List<String> {
        var current = ""
        var lines = mutableListOf<String>()
        var i = maxLength
        for (character in str) {
            val isNewline = character == '\n'
            if (i == 0 || isNewline) {
                lines.add(current.trim())
                current = ""
                i = maxLength
            }
            if (!isNewline) {
                current += character
            }
            i--
        }
        if (current.length > 0) lines.add(current.trim().padEnd(maxLength, ' '))
        return lines
    }

    private fun shortenOrPad(str: String, length: Int): String =
            if (str.length <= length) {
                str.padEnd(length, ' ')
            } else {
                ".." + str.substring(str.length - length + 2)
            }
}
