package org.javacs.kt

import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import java.util.logging.Formatter
import java.util.logging.LogRecord
import java.util.logging.Logger

val LOG = LogFormat.createLogger()

object LogFormat: Formatter() {
    fun createLogger(): Logger {
        val result = Logger.getLogger("main")
        val root = Logger.getLogger("")

        for (each in root.handlers) {
            each.formatter = LogFormat
        }

        return result
    }

    private const val format = "%1\$tT.%1\$tL %2\$s %3\$s %4\$s %5\$s%6\$s%n"
    private val date = Date()

    private var maxSource = 0
    private var maxThread = 0

    override fun format(record: LogRecord): String {
        date.time = record.millis
        var source: String
        if (record.sourceClassName != null) {
            source = record.sourceClassName.split(".").last()
            if (record.sourceMethodName != null) {
                source += "#" + record.sourceMethodName
            }
        } else {
            source = record.loggerName
        }
        maxSource = Math.max(maxSource, source.length + 1)
        maxSource = Math.min(maxSource, 50)
        source = source.padEnd(maxSource, ' ')

        val message = formatMessage(record)
        var throwable = ""
        if (record.thrown != null) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            pw.println()
            record.thrown.printStackTrace(pw)
            pw.close()
            throwable = sw.toString()
        }

        var thread = Thread.currentThread().name
        maxThread = Math.max(maxThread, thread.length + 1)
        maxThread = Math.min(maxThread, 20)
        thread = thread.padEnd(maxThread, ' ')

        return String.format(
                format, date, record.level.localizedName, thread, source, message, throwable)
    }
}