package org.javacs.kt.util

/**
 * Tasks only for Windows
 */
class WindowsContext : OSContext {
    override fun candidateAlternativeLibraryLocations(name: String): Array<String> = // Scoop (https://scoop.sh)
        CANDIDATE_PATHS.map {
            "$it$name.jar"
        }.toTypedArray()
    companion object {
        /**
         * Absolute path to the user's profile folder (home directory)
         */
        private val USERPROFILE = System.getenv("USERPROFILE")
        private val CANDIDATE_PATHS = arrayOf(
            "${USERPROFILE}\\scoop\\apps\\kotlin\\current\\lib\\",
        )
    }
}
