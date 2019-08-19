package org.javacs.kt.externalsources

import org.javacs.kt.CompilerClassPath
import org.javacs.kt.ExternalSourcesConfiguration
import org.javacs.kt.j2k.convertJavaToKotlin
import java.io.BufferedReader
import java.nio.file.Files

/**
 * Provides the source code for classes located inside
 * compiled or source JARs.
 */
class JarClassContentProvider(
    private val config: ExternalSourcesConfiguration,
    private val cp: CompilerClassPath,
    private val decompiler: Decompiler = FernflowerDecompiler()
) {
    private val cachedContents = mutableMapOf<KlsURI, String>()

    /**
     * Fetches the contents of a compiled class/source file in a JAR
     * and another URI which can be used to refer to these extracted
     * contents.
     */
    public fun contentsOf(uri: KlsURI): Pair<KlsURI, String> {
        val sourceUri = uri.withFileExtension(if (config.autoConvertToKotlin) "kt" else "java")
        val contents = cachedContents[sourceUri] ?: findContentsOf(uri).also { cachedContents[sourceUri] = it }
        return Pair(sourceUri, contents)
    }

    private fun convertToKotlinIfNeeded(javaCode: String): String = if (config.autoConvertToKotlin) {
        convertJavaToKotlin(javaCode, cp.compiler)
    } else {
        javaCode
    }

    private fun findContentsOf(uri: KlsURI): String = when (uri.fileExtension) {
        "class" -> uri.extractToTemporaryFile()
            .let(decompiler::decompileClass)
            .let { Files.newInputStream(it) }
            .bufferedReader()
            .use(BufferedReader::readText)
            .let(this::convertToKotlinIfNeeded)
        "java" -> convertToKotlinIfNeeded(uri.readContents())
        else -> uri.readContents() // e.g. for Kotlin source files
    }
}
