package org.javacs.kt.formatting

import com.facebook.ktfmt.format
import com.facebook.ktfmt.FormattingOptions as KtfmtOptions
import org.eclipse.lsp4j.FormattingOptions

fun formatKotlinCode(
    code: String,
    isScript: Boolean = false,
    options: FormattingOptions = FormattingOptions(4, true)
): String = format(KtfmtOptions(
    style = KtfmtOptions.Style.GOOGLE,
    blockIndent = options.tabSize,
    continuationIndent = 2 * options.tabSize
), code)
