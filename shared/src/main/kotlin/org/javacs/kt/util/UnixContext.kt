package org.javacs.kt.util

/**
 * Tasks for other than Windows
 */
class UnixContext : OSContext {
    override fun candidateAlternativeLibraryLocations(name: String): Array<String> = // Snap (Linux)
        arrayOf("/snap/kotlin/current/lib/${name}.jar")
}
