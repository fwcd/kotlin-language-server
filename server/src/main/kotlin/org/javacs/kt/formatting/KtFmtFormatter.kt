package org.javacs.kt.formatting

import org.javacs.kt.KtFmtConfiguration
import com.facebook.ktfmt.format.Formatter as KtFmt
import com.facebook.ktfmt.format.FormattingOptions as KtfmtOptions
import org.eclipse.lsp4j.FormattingOptions as LspFormattingOptions

class KtFmtFormatter(private val config: KtFmtConfiguration) : Formatter {
    override fun format(
        code: String,
        options: LspFormattingOptions,
    ): String {
        val style = when (config.style) {
            "google" -> KtfmtOptions.Style.GOOGLE
            "facebook" -> KtfmtOptions.Style.FACEBOOK
            "dropbox" -> KtfmtOptions.Style.DROPBOX
            else -> KtfmtOptions.Style.GOOGLE
        }
        return KtFmt.format(KtfmtOptions(
            style = style,
            maxWidth = config.maxWidth,
            blockIndent = options.tabSize.takeUnless { it == 0 } ?: config.indent,
            continuationIndent = config.continuationIndent,
            removeUnusedImports = config.removeUnusedImports,
        ), code)
    }
}

