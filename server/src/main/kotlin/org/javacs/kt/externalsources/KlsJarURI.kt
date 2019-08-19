package org.javacs.kt.externalsources

import java.net.URI
import java.net.URL
import java.net.JarURLConnection
import java.io.BufferedReader
import java.io.File.createTempFile
import java.nio.file.Path

fun URI.toKlsJarURI(): KlsJarURI? = if (scheme == "kls") KlsJarURI(this) else null

/**
 * A Uniform Resource Identifier (URI) with a "kls" (Kotlin language server) scheme.
 * The URI should be structured as follows:
 *
 * <p><pre>
 * kls:file:///path/to/jarFile.jar!/path/to/jvmClass.class
 * </pre></p>
 *
 * Other file extensions for classes (such as .kt and .java) are supported too, in
 * which case the file will directly be used without invoking the decompiler.
 */
class KlsJarURI(private val uri: URI) {
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

    fun readToTemporaryFile(): Path = withJarURLConnection {
        val name = it.jarEntry.name.split(".")
        val tmpFile = createTempFile(name[0], name[1])
        tmpFile.deleteOnExit()

        it.jarFile
            .getInputStream(it.jarEntry)
            .use { input -> tmpFile.outputStream().use { input.copyTo(it) } }

        tmpFile.toPath()
    }
}
