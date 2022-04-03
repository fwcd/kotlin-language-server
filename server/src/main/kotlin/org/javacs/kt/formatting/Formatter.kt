package org.javacs.kt.formatting

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.FormattingOptions as KtfmtOptions
import org.eclipse.lsp4j.FormattingOptions

fun formatKotlinCode(
    code: String,
    options: FormattingOptions = FormattingOptions(4, true)
): String = Formatter.format(KtfmtOptions(
    style = KtfmtOptions.Style.GOOGLE,
    blockIndent = options.tabSize,
    continuationIndent = 2 * options.tabSize
), code)
