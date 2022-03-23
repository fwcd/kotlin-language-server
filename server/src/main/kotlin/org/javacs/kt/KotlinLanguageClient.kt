package org.javacs.kt

import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import org.eclipse.lsp4j.services.LanguageClient

@JsonSegment("kotlin")
interface KotlinLanguageClient : LanguageClient {

    @JsonNotification
    fun buildOutputLocationSet(buildOutputLocation: String) {
        throw UnsupportedOperationException()
    }
}
