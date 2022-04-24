package org.javacs.kt

import org.eclipse.lsp4j.*
import org.javacs.kt.util.AsyncExecutor
import org.javacs.kt.util.parseURI
import java.util.concurrent.CompletableFuture

class KotlinProtocolExtensionService(
    private val uriContentProvider: URIContentProvider,
    private val cp: CompilerClassPath
) : KotlinProtocolExtensions {
    private val async = AsyncExecutor()

    override fun jarClassContents(textDocument: TextDocumentIdentifier): CompletableFuture<String?> = async.compute {
        uriContentProvider.contentOf(parseURI(textDocument.uri))
    }

    override fun buildOutputLocation(): CompletableFuture<String?> = async.compute {
        cp.outputDirectory.absolutePath
    }
}
