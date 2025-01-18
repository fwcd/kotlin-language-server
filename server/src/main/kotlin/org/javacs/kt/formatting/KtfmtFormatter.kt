package org.javacs.kt.formatting

import org.javacs.kt.KtfmtConfiguration
import com.facebook.ktfmt.format.Formatter as Ktfmt
import com.facebook.ktfmt.format.FormattingOptions as KtfmtOptions
import org.eclipse.lsp4j.FormattingOptions as LspFormattingOptions

class KtfmtFormatter(private val config: KtfmtConfiguration) : Formatter {
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
        return Ktfmt.format(
            KtfmtOptions(
                style = style,
            maxWidth = config.maxWidth,
            blockIndent = options.tabSize.takeUnless { it == 0 } ?: config.indent,
            continuationIndent = config.continuationIndent,
            removeUnusedImports = config.removeUnusedImports,
        ), code)
    }
}

