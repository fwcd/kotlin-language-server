package org.javacs.kt.formatting

import org.eclipse.lsp4j.FormattingOptions

fun formatKotlinCode(
	code: String,
    isScript: Boolean = false,
    options: FormattingOptions = FormattingOptions(4, true)
): String = code // TODO

// KtLint.format(
// 	KtLint.Params(
// 		text = code,
// 		script = isScript,
//         ruleSets = listOf(StandardRuleSetProvider().get()),
//         userData = mapOf(
//             "indent_size" to options.tabSize.toString(),
//             "indent_style" to if (options.isInsertSpaces) "space" else "tab"
//         ),
// 		cb = lintCallback
// 	)
// )
