package org.javacs.kt.formatting

import org.eclipse.lsp4j.FormattingOptions
import com.pinterest.ktlint.core.KtLint
import com.pinterest.ktlint.core.LintError
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider

fun formatKotlinCode(
	code: String,
	lintCallback: (error: LintError, corrected: Boolean) -> Unit = { _, _ -> Unit },
    isScript: Boolean = false,
    options: FormattingOptions = FormattingOptions(4, true)
): String = KtLint.format(
	KtLint.Params(
		text = code,
		script = isScript,
        ruleSets = listOf(StandardRuleSetProvider().get()),
        userData = mapOf(
            "indent_size" to options.tabSize.toString(),
            "indent_style" to if (options.isInsertSpaces) "space" else "tab"
        ),
		cb = lintCallback
	)
)
