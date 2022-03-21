package org.javacs.kt.externalsources

import org.javacs.kt.CompilerClassPath
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class ClassPathSourceArchiveProvider(
    private val cp: CompilerClassPath
) : SourceArchiveProvider {
    override fun fetchSourceArchive(compiledArchive: Path): Path? =
        getJdkSource(compiledArchive) ?: cp.classPath.firstOrNull { it.compiledJar == compiledArchive }?.sourceJar

    /**
     * Checks if the given path is inside the JDK. If it is, we return the corresponding source zip.
     * Note that this method currently doesn't take into the account the JDK version, which means JDK source code
     * is only available for JDK 9+ builds.
     * TODO: improve this resolution logic to work for older JDK versions as well.
     */
    private fun getJdkSource(path: Path): Path? {
        cp.javaHome?.let {
            val javaHomePath = File(it).toPath()
            if (path == javaHomePath) {
                return Paths.get(path.toString(), "lib", "src.zip")
            }
        }
        return null
    }
}
