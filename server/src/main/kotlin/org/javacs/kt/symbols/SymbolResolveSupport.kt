package org.javacs.kt.symbols

import org.eclipse.lsp4j.ClientCapabilities

data class SymbolResolveSupport(
    val enabled: Boolean = false,
    val properties: List<String> = emptyList()
)

val ClientCapabilities?.symbolResolveSupport
    get() = this?.workspace?.symbol?.resolveSupport?.properties?.let { properties ->
        if (properties.size > 0) SymbolResolveSupport(true, properties) else null
    } ?: SymbolResolveSupport(false, emptyList())
