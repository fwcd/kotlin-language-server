package org.javacs.kt

data class ExclusionsConfiguration(
    var excludePatterns: List<String> = listOf(), // List of glob patterns
)
