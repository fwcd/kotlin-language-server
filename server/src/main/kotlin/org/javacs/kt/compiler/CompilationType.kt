package org.javacs.kt.compiler

/**
 * Determines the compilation environment used
 * by the compiler (and thus the class path).
 */
enum class CompilationType {
    /** Uses the default class path. */
    DEFAULT,
    /** Uses the Kotlin DSL class path if available. */
    BUILD_SCRIPT
}
