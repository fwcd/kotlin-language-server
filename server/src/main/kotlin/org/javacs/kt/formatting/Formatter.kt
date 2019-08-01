package org.javacs.kt.formatting

import com.pinterest.ktlint.core.KtLint
import com.pinterest.ktlint.core.LintError
import com.pinterest.ktlint.ruleset.standard.StandardRuleSetProvider

fun formatKotlinCode(
	code: String,
	lintCallback: (error: LintError, corrected: Boolean) -> Unit = { _, _ -> Unit },
	isScript: Boolean = false
): String = KtLint.format(
	KtLint.Params(
		text = code,
		script = isScript,
		ruleSets = listOf(StandardRuleSetProvider().get()),
		cb = lintCallback
	)
)
