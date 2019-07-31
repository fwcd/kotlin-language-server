package org.javacs.kt.util

import java.time.Duration
import java.util.function.Supplier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

private var threadCount = 0

class AsyncExecutor {
	private val workerThread = Executors.newSingleThreadExecutor { Thread(it, "async${threadCount++}") }

	fun <R> compute(task: () -> R) =
			CompletableFuture.supplyAsync(Supplier(task), workerThread)

	fun <R> computeOr(defaultValue: R, task: () -> R?) =
			CompletableFuture.supplyAsync(Supplier {
				try {
					task() ?: defaultValue
				} catch (e: Exception) {
					defaultValue
				}
			}, workerThread)
	
	fun shutdown() {
		workerThread.shutdown()
	}
}
