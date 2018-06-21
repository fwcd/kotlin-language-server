package org.javacs.kt.util

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.jsonrpc.CompletableFutures

fun <R> computeAsync(code: () -> R): CompletableFuture<R> =
		CompletableFutures.computeAsync { _ -> code() }
