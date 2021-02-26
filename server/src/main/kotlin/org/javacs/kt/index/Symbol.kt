package org.javacs.kt.index

import org.jetbrains.kotlin.name.FqName

data class Symbol(
    // TODO: Store location (e.g. using a URI)
    val fqName: FqName,
    val kind: Kind
) {
    enum class Kind(val rawValue: Int) {
        CLASS(0),
        INTERFACE(1),
        FUNCTION(2),
        VARIABLE(3),
        MODULE(4),
        ENUM(5),
        ENUM_MEMBER(6),
        CONSTRUCTOR(7),
        FIELD(8);

        companion object {
            fun fromRaw(rawValue: Int) = Kind.values().first { it.rawValue == rawValue }
        }
    }
}
