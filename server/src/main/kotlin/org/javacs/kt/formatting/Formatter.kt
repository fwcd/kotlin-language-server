package org.javacs.kt.formatting

import org.eclipse.lsp4j.FormattingOptions as LspFromattingOptions

interface Formatter {
    fun format(code: String, options: LspFromattingOptions): String
}

