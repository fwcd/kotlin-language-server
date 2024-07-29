package org.javacs.kt.util

/**
 * Tasks that depends on user's OS
 */
interface OSContext {
    /**
     * Suggests the candidate locations of the given JAR
     *
     * @param name the name of the JAR
     * @return the candidate full paths to the JAR
     */
    fun candidateAlternativeLibraryLocations(name: String): Array<String>

    companion object {
        /**
         * Gets the instance for the current OS
         */
        val CURRENT_OS by lazy<OSContext> {
            val osName = System.getProperty("os.name")!!.lowercase()
            when {
                osName.contains("windows") -> WindowsContext()
                else -> UnixContext()
            }
        }
    }
}
