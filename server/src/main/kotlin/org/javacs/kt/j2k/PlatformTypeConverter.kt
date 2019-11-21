package org.javacs.kt.j2k

private val platformImports = setOf(
    "java.util.List",
    "java.util.Set"
)

fun isPlatformImport(fqJavaType: String) =
    platformImports.contains(fqJavaType)

fun platformType(javaType: String): String = when (javaType) {
    "List" -> "MutableList"
    "Set" -> "MutableSet"
    "Collection" -> "MutableCollection"
    "Integer" -> "Int"
    "Character" -> "Char"
    else -> javaType
}
