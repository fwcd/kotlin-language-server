package org.javacs.kt.externalsources

import org.javacs.kt.util.partitionAroundLast
import org.javacs.kt.util.TemporaryDirectory
import org.javacs.kt.util.parseURI
import java.net.URI
import java.net.URL
import java.net.JarURLConnection
import java.io.BufferedReader
import java.io.File
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile

fun URI.toKlsURI(): KlsURI? = when (scheme) {
    "kls" -> KlsURI(URI("kls:${schemeSpecificPart.replace(" ", "%20")}"))
    "file" -> KlsURI(URI("kls:$this"))
    else -> null
}

/**
 * Identifies a class or source file inside an archive using a Uniform
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
data class KlsURI(val fileUri: URI, val query: Map<QueryParam, String>) {
    /** Possible KLS URI query parameters. */
    enum class QueryParam(val parameterName: String) {
        SOURCE("source");

        override fun toString(): String = parameterName
    }

    enum class ArchiveType(val delimiter: String) {
        JAR("!"),
        ZIP("!"),
        JDK("!/modules")
    }

    private val queryString: String
        get() = if (query.isEmpty()) "" else query.entries.fold("?") { accum, next -> "$accum${next.key}=${next.value}" }

    val fileName: String
        get() = fileUri.toString().substringAfterLast("/")
    val fileExtension: String?
        get() = fileName
            .split(".")
            .takeIf { it.size > 1 }
            ?.lastOrNull()

    private val archiveType: ArchiveType
        get() = when {
            fileUri.schemeSpecificPart.contains("!/modules") -> {
                ArchiveType.JDK
            }
            fileUri.schemeSpecificPart.contains(".zip!") -> {
                ArchiveType.ZIP
            }
            else -> {
                ArchiveType.JAR
            }
        }

    val archivePath: Path
        get() = Paths.get(parseURI(fileUri.schemeSpecificPart.split(archiveType.delimiter)[0]))

    private val innerPath: String
        get() = fileUri.schemeSpecificPart.split(archiveType.delimiter, limit = 2)[1]

    val source: Boolean
        get() = query[QueryParam.SOURCE]?.toBoolean() ?: false

    constructor(uri: URI) : this(parseKlsURIFileURI(uri), parseKlsURIQuery(uri))

    // If the newArchivePath doesn't have the kls scheme, it is added in the returned KlsURI.
    fun withArchivePath(newArchivePath: Path): KlsURI? =
        URI(newArchivePath.toUri().toString() + (innerPath.let { "!$it" } )).toKlsURI()?.let { KlsURI(it.fileUri, query) }

    fun withFileExtension(newExtension: String): KlsURI {
        val (parentUri, fileName) = fileUri.toString().partitionAroundLast("/")
        val newUri = URI("$parentUri${fileName.split(".").first()}.$newExtension")
        return KlsURI(newUri, query)
    }

    fun withSource(source: Boolean): KlsURI {
        val newQuery: MutableMap<QueryParam, String> = mutableMapOf()
        newQuery.putAll(query)
        newQuery[QueryParam.SOURCE] = source.toString()
        return KlsURI(fileUri, newQuery)
    }

    private fun toURI(): URI = URI(fileUri.toString() + queryString)

    private fun toJarURL(): URL = URL("jar:${fileUri.schemeSpecificPart}")

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

    fun readContents(): String = when (archiveType) {
        ArchiveType.ZIP -> {
            val zipFile = ZipFile(File("$archivePath"))
            zipFile.getInputStream(zipFile.getEntry(innerPath.trimStart('/')))
                .bufferedReader()
                .use(BufferedReader::readText)
        }
        ArchiveType.JAR, ArchiveType.JDK -> {
            withJarURLConnection {
                it.jarFile
                    .getInputStream(it.jarEntry)
                    .bufferedReader()
                    .use(BufferedReader::readText)
            }
        }
    }

    fun extractToTemporaryFile(dir: TemporaryDirectory): Path = withJarURLConnection {
        val name = it.jarEntry.name.substringAfterLast("/").split(".")
        val tmpFile = dir.createTempFile(name[0], ".${name[1]}")

        it.jarFile
            .getInputStream(it.jarEntry)
            .use { input -> Files.newOutputStream(tmpFile).use { input.copyTo(it) } }

        tmpFile
    }

    override fun toString(): String = toURI().toString()
}

private fun parseKlsURIFileURI(uri: URI): URI = URI(uri.toString().split("?")[0])

private fun parseKlsURIQuery(uri: URI): Map<KlsURI.QueryParam, String> = parseQuery(uri.toString().split("?").getOrElse(1) { "" })

private fun parseQuery(query: String): Map<KlsURI.QueryParam, String> =
    query.split("&").mapNotNull {
        val parts = it.split("=")
        if (parts.size == 2) getQueryParameter(parts[0], parts[1]) else null
    }.toMap()

private fun getQueryParameter(property: String, value: String): Pair<KlsURI.QueryParam, String>? {
    val queryParam: KlsURI.QueryParam? = KlsURI.QueryParam.values().find { it.parameterName == property }

    if (queryParam != null) {
        return Pair(queryParam, value)
    }

    return null
}
