package org.javacs.kt

public data class SnippetsConfiguration(
    var enabled: Boolean = true
)

public data class CompletionConfiguration(
    val snippets: SnippetsConfiguration = SnippetsConfiguration()
)

public data class LintingConfiguration(
    var debounceTime: Long = 250L
)

public data class Configuration(
    val completion: CompletionConfiguration = CompletionConfiguration(),
    val linting: LintingConfiguration = LintingConfiguration()
)
