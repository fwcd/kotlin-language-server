package org.javacs.kt

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import org.javacs.kt.externalsources.JarClassContentProvider
import org.javacs.kt.externalsources.toKlsURI
import org.javacs.kt.util.KotlinLSException

/**
 * Fetches the content of Kotlin files identified by a URI.
 */
class URIContentProvider(
    private val sp: SourcePath,
    val jarClassContentProvider: JarClassContentProvider
) {
    fun contentOfEncoded(uri: String) = contentOf(URI(URLDecoder.decode(uri, StandardCharsets.UTF_8.name())))

    fun contentOf(uri: URI): String = when (uri.scheme) {
        "file" -> sp.content(Paths.get(uri))
        "kls" -> uri.toKlsURI()?.let { jarClassContentProvider.contentOf(it).second }
            ?: throw KotlinLSException("Could not find ${uri}")
        else -> throw KotlinLSException("Unrecognized scheme ${uri.scheme}")
    }
}
