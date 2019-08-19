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
    private val contentProvider: URIContentProvider
) : KotlinProtocolExtensions {
    private val async = AsyncExecutor()

    override fun jarClassContents(textDocument: TextDocumentIdentifier): CompletableFuture<String?> = async.compute {
        contentProvider.contentOfEncoded(textDocument.uri)
    }
}
