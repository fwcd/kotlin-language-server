package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture

@JsonSegment("kotlin")
interface KotlinProtocolExtensions {
    @JsonRequest
    fun jarClassContents(textDocument: TextDocumentIdentifier): CompletableFuture<String?>

    @JsonRequest
    fun buildOutputLocation(): CompletableFuture<String?>

    @JsonRequest
    fun mainClass(textDocument: TextDocumentIdentifier): CompletableFuture<Map<String, Any?>>
}
