package org.javacs.kt.formatting

import org.eclipse.lsp4j.FormattingOptions as LspFormattingOptions

interface Formatter {
    fun format(code: String, options: LspFormattingOptions): String
}

