package org.javacs.kt

import org.eclipse.lsp4j.*
import org.javacs.kt.externalsources.Decompiler
import org.javacs.kt.externalsources.toKlsJarURI
import org.javacs.kt.util.AsyncExecutor
import org.javacs.kt.util.noResult
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.CompletableFuture

class KotlinProtocolExtensionService(
    val decompiler: Decompiler
) : KotlinProtocolExtensions {
    private val async = AsyncExecutor()

    override fun jarClassContents(textDocument: TextDocumentIdentifier): CompletableFuture<String?> = async.compute {
        val uri = try { URI(textDocument.uri) } catch (e: URISyntaxException) { null }
        val klsURI = uri?.toKlsJarURI()
        klsURI?.readDecompiled(decompiler) ?: noResult("Could not fetch JAR class contents of '${textDocument.uri}'. Make sure that it conforms to the format 'kls:file:///path/to/myJar.jar!/[...]'!", null)
    }
}
