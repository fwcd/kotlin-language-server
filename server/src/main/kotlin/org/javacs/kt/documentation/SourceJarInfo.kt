package org.javacs.kt.documentation

import java.nio.file.Path

data class SourceJarInfo(
    val packageName: String,
    val sourceJar: Path,
    val fileName: String,
    val functions: List<String>,
    val types: List<String>,
)
