package org.javacs.kt.externalsources

import org.javacs.kt.j2k.convertJavaToKotlin
import org.javacs.kt.util.partitionAroundLast
import java.net.URI
import java.net.URL
import java.net.JarURLConnection
import java.io.BufferedReader
import java.io.File.createTempFile
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

fun URI.toKlsURI(): KlsURI? = when (scheme) {
    "kls" -> KlsURI(URI("kls:$schemeSpecificPart"))
    "file" -> KlsURI(URI("kls:$this"))
    else -> null
}

/**
 * Identifies a class or source file inside a JAR archive using a Uniform
 * Resource Identifier (URI) with a "kls" (Kotlin language server) scheme.
 * The URI should be structured as follows:
 *
 * <p><pre>
 * kls:file:///path/to/jarFile.jar!/path/to/jvmClass.class
 * </pre></p>
 *
 * Other file extensions for classes (such as .kt and .java) are supported too, in
 * which case the file will directly be used without invoking the decompiler.
 */
data class KlsURI(val uri: URI) {
    val fileExtension: String?
        get() = uri.toString()
            .substringAfterLast("/")
            .split(".")
            .takeIf { it.size > 1 }
            ?.lastOrNull()
    val isCompiled: Boolean
        get() = fileExtension == "class"

    fun withFileExtension(newExtension: String): KlsURI {
        val (parentURI, fileName) = uri.toString().partitionAroundLast("/")
        val newURI = "$parentURI${fileName.split(".").first()}.$newExtension"
        return KlsURI(URI(newURI))
    }

    private fun toFileURI(): URI = URI(uri.schemeSpecificPart)

    private fun toJarURL(): URL = URL("jar:${uri.schemeSpecificPart}")

    private fun openJarURLConnection() = toJarURL().openConnection() as JarURLConnection

    private inline fun <T> withJarURLConnection(action: (JarURLConnection) -> T): T {
        val connection = openJarURLConnection()
        val result = action(connection)
        // https://bugs.openjdk.java.net/browse/JDK-8080094
        if (connection.useCaches) {
            connection.jarFile.close()
        }
        return result
    }

    fun readContents(): String = withJarURLConnection {
        it.jarFile
            .getInputStream(it.jarEntry)
            .bufferedReader()
            .use(BufferedReader::readText)
    }

    fun extractToTemporaryFile(): Path = withJarURLConnection {
        val name = it.jarEntry.name.split(".")
        val tmpFile = createTempFile(name[0], ".${name[1]}")
        tmpFile.deleteOnExit()

        it.jarFile
            .getInputStream(it.jarEntry)
            .use { input -> tmpFile.outputStream().use { input.copyTo(it) } }

        tmpFile.toPath()
    }

    override fun toString(): String = uri.toString()
}
