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

public data class JVMConfiguration(
    var target: String = "default" // See Compiler.jvmTargetFrom for possible values
)

public data class CompilerConfiguration(
    val jvm: JVMConfiguration = JVMConfiguration()
)

public data class URIConfiguration(
    var useKlsScheme: Boolean = false
)

public data class Configuration(
    val compiler: CompilerConfiguration = CompilerConfiguration(),
    val completion: CompletionConfiguration = CompletionConfiguration(),
    val linting: LintingConfiguration = LintingConfiguration(),
    val uri: URIConfiguration = URIConfiguration()
)
