package org.javacs.kt.progress

import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressNotification
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.WorkDoneProgressEnd

class LanguageClientProgress(
    private val label: String,
    private val token: Either<String, Number>,
    private val client: LanguageClient
) : Progress {
    init {
        reportProgress(WorkDoneProgressBegin().also {
            it.title = "Kotlin: $label"
            it.percentage = 0
        })
    }

    override fun update(message: String?, percent: Int?) {
        reportProgress(WorkDoneProgressReport().also {
            it.message = message
            it.percentage = percent
        })
    }

    override fun close() {
        reportProgress(WorkDoneProgressEnd())
    }

    private fun reportProgress(notification: WorkDoneProgressNotification) {
        client.notifyProgress(ProgressParams(token, notification))
    }
}
