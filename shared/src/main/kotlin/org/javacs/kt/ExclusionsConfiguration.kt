package org.javacs.kt

data class ExclusionsConfiguration(
    var excludePatterns: List<String> = listOf(), // Semicolon-separated list of glob patterns
)
