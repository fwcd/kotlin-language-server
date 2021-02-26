package org.javacs.kt.index

import org.jetbrains.kotlin.name.FqName

data class Symbol(
    // TODO: Store location (e.g. using a URI)
    private val fqName: FqName,
    private val kind: Kind
) {
    enum class Kind {
        CLASS,
        INTERFACE,
        FUNCTION,
        VARIABLE
    }
}
