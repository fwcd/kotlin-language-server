package org.javacs.kt.externalsources

import org.javacs.kt.CompilerClassPath
import org.javacs.kt.ExternalSourcesConfiguration
import org.javacs.kt.LOG
import org.javacs.kt.util.describeURI
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.TemporaryDirectory
import org.javacs.kt.j2k.convertJavaToKotlin
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.nio.file.Files
import java.util.LinkedHashMap

/**
 * Provides the source code for classes located inside
 * compiled or source JARs.
 */
class JarClassContentProvider(
    private val config: ExternalSourcesConfiguration,
    private val cp: CompilerClassPath,
    private val tempDir: TemporaryDirectory,
    private val decompiler: Decompiler = FernflowerDecompiler()
) {
    /** Maps recently used (source-)KLS-URIs to their source contents (e.g. decompiled code). */
    private val cachedContents = object : LinkedHashMap<String, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 5
    }

    /**
     * Fetches the contents of a compiled class/source file in a JAR
     * and another URI which can be used to refer to these extracted
     * contents.
     */
    public fun contentOf(uri: KlsURI): Pair<KlsURI, String> {
        val sourceURI = uri.withFileExtension(if (config.autoConvertToKotlin) "kt" else "java")
        val key = sourceURI.toString()
        val contents: String = cachedContents[key] ?: run {
            LOG.info("Finding contents of {}", describeURI(uri.uri))
            tryReadContentOf(uri)
                ?: tryReadContentOf(uri.withFileExtension("class"))
                ?: tryReadContentOf(uri.withFileExtension("java"))
                ?: tryReadContentOf(uri.withFileExtension("kt"))
                ?: throw KotlinLSException("Could not find $uri")
        }.also { cachedContents[key] = it }
        return Pair(sourceURI, contents)
    }

    private fun convertToKotlinIfNeeded(javaCode: String): String = if (config.autoConvertToKotlin) {
        convertJavaToKotlin(javaCode, cp.compiler)
    } else {
        javaCode
    }

    private fun tryReadContentOf(uri: KlsURI): String? = try {
        when (uri.fileExtension) {
            "class" -> uri.extractToTemporaryFile(tempDir)
                .let(decompiler::decompileClass)
                .let { Files.newInputStream(it) }
                .bufferedReader()
                .use(BufferedReader::readText)
                .let(this::convertToKotlinIfNeeded)
            "java" -> convertToKotlinIfNeeded(uri.readContents())
            else -> uri.readContents() // e.g. for Kotlin source files
        }
    } catch (e: FileNotFoundException) { null }
}
