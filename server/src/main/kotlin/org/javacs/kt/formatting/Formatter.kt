package org.javacs.kt.formatting

import org.javacs.kt.Configuration
import org.javacs.kt.FormattingConfiguration
import org.eclipse.lsp4j.FormattingOptions as LspFromattingOptions

private const val DEFAULT_INDENT = 4

class FormattingService(private val config: FormattingConfiguration) {

    private val formatter: Formatter get() = when (config.formatter) {
        "ktfmt" -> KtFmtFormatter(config.ktFmt)
        "none" -> NopFormatter
        else -> KtFmtFormatter(config.ktFmt)
    }

    fun formatKotlinCode(
        code: String,
        options: LspFromattingOptions = LspFromattingOptions(DEFAULT_INDENT, true)
    ): String = this.formatter.format(code, options)
}


interface Formatter {
    fun format(code: String, options: LspFromattingOptions): String
}

object NopFormatter : Formatter {
    override fun format(code: String, options: LspFromattingOptions): String = code
}

