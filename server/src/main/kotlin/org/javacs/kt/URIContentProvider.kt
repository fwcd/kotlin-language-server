package org.javacs.kt

import java.net.URI
import java.nio.file.Paths
import org.javacs.kt.externalsources.ClassContentProvider
import org.javacs.kt.externalsources.toKlsURI
import org.javacs.kt.util.KotlinLSException

/**
 * Fetches the content of Kotlin files identified by a URI.
 */
class URIContentProvider(
    val classContentProvider: ClassContentProvider
) {
    fun contentOf(uri: URI): String = when (uri.scheme) {
        "file" -> Paths.get(uri).toFile().readText()
        "kls" -> uri.toKlsURI()?.let { classContentProvider.contentOf(it).second }
            ?: throw KotlinLSException("Could not find $uri")
        else -> throw KotlinLSException("Unrecognized scheme ${uri.scheme}")
    }
}
