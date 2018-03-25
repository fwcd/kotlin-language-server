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

    private val format = "%1\$tF\t%1\$tT\t%4\$s\t%2\$s\t%5\$s%6\$s%n"
    private val date = Date()

    override fun format(record: LogRecord): String {
        date.time = record.millis
        var source: String
        if (record.sourceClassName != null) {
            source = record.sourceClassName
            if (record.sourceMethodName != null) {
                source += " " + record.sourceMethodName
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
            throwable = sw.toString()
        }
        return String.format(
                format, date, source, record.loggerName, record.level.localizedName, message, throwable)
    }
}