package org.javacs.kt

public data class SnippetsConfiguration(
    /** Whether code completion should return VSCode-style snippets. */
    var enabled: Boolean = true
)

public data class CompletionConfiguration(
    val snippets: SnippetsConfiguration = SnippetsConfiguration()
)

public data class LintingConfiguration(
    /** The time interval between subsequent lints in ms. */
    var debounceTime: Long = 250L
)

public data class JVMConfiguration(
    /** Which JVM target the Kotlin compiler uses. See Compiler.jvmTargetFrom for possible values. */
    var target: String = "default"
)

public data class CompilerConfiguration(
    val jvm: JVMConfiguration = JVMConfiguration()
)

public data class IndexingConfiguration(
    /** Whether an index of global symbols should be built in the background. */
    var enabled: Boolean = true
)

public data class ExternalSourcesConfiguration(
    /** Whether kls-URIs should be sent to the client to describe classes in JARs. */
    var useKlsScheme: Boolean = false,
    /** Whether external classes should be automatically converted to Kotlin. */
    var autoConvertToKotlin: Boolean = false
)

public data class Configuration(
    val compiler: CompilerConfiguration = CompilerConfiguration(),
    val completion: CompletionConfiguration = CompletionConfiguration(),
    val linting: LintingConfiguration = LintingConfiguration(),
    var indexing: IndexingConfiguration = IndexingConfiguration(),
    val externalSources: ExternalSourcesConfiguration = ExternalSourcesConfiguration()
)
