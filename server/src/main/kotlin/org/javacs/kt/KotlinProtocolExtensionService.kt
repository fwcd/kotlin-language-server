package org.javacs.kt

import org.eclipse.lsp4j.*
import org.javacs.kt.externalsources.JarClassContentProvider
import org.javacs.kt.externalsources.toKlsURI
import org.javacs.kt.util.AsyncExecutor
import org.javacs.kt.util.noResult
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.CompletableFuture

class KotlinProtocolExtensionService(
    private val jarClassContentProvider: JarClassContentProvider
) : KotlinProtocolExtensions {
    private val async = AsyncExecutor()

    override fun jarClassContents(textDocument: TextDocumentIdentifier): CompletableFuture<String?> = async.compute {
        val uri = try { URI(textDocument.uri) } catch (e: URISyntaxException) { null }
        uri?.toKlsURI()
            ?.let { klsURI ->
                val (_, contents) = jarClassContentProvider.contentsOf(klsURI)
                contents
            }
            ?: noResult("Could not fetch JAR class contents of '${textDocument.uri}'. Make sure that it conforms to the format 'kls:file:///path/to/myJar.jar!/[...]'!", null)
    }
}
