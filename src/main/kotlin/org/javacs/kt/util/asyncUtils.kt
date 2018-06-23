package org.javacs.kt.util

import java.time.Duration
import java.util.function.Supplier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

private val workerThread = Executors.newSingleThreadExecutor { Thread(it, "async") }

fun <R> computeAsync(code: () -> R) =
		CompletableFuture.supplyAsync(Supplier(code), workerThread)
