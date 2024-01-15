package org.javacs.kt

public data class ScriptsConfiguration(
    /** Whether .kts scripts are handled. */
    var enabled: Boolean = false,
    /** Whether .gradle.kts scripts are handled. Only considered if scripts are enabled in general. */
    var buildScriptsEnabled: Boolean = false
)
