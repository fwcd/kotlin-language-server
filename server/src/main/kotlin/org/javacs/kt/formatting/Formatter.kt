package org.javacs.kt.formatting

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.FormattingOptions as KtfmtOptions
import org.eclipse.lsp4j.FormattingOptions

fun formatKotlinCode(
    code: String,
    options: FormattingOptions = FormattingOptions(4, true)
): String = Formatter.format(KtfmtOptions(
    // If option.tabSize is 2 or 4, the generated KtfmtOptions instance concides with one of the two presets in
    // https://github.com/facebookincubator/ktfmt/blob/d4718f643abd0999ba502caf5062c98a3218e88d/core/src/main/java/com/facebook/ktfmt/format/Formatter.kt#L45-L50
    style = KtfmtOptions.Style.GOOGLE,
    blockIndent = options.tabSize,
    continuationIndent = options.tabSize
), code)
