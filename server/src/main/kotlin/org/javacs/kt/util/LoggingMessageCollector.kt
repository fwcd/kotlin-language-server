package org.javacs.kt.util

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.javacs.kt.LOG

object LoggingMessageCollector: MessageCollector {
	override fun clear() {}

	override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
		LOG.debug("Kotlin compiler: [{}] {} @ {}", severity, message, location)
	}

	override fun hasErrors() = false
}
