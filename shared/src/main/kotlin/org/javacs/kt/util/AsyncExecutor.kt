package org.javacs.kt.util

import org.javacs.kt.LOG
import java.time.Duration
import java.util.function.Supplier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private var threadCount = 0

class AsyncExecutor {
	private val workerThread = Executors.newSingleThreadExecutor { Thread(it, "async${threadCount++}") }

    fun execute(task: () -> Unit) =
            CompletableFuture.runAsync(Runnable(task), workerThread)

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

	fun shutdown(awaitTermination: Boolean) {
		workerThread.shutdown()
		if (awaitTermination) {
			LOG.info("Awaiting async termination...")
			workerThread.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
		}
	}
}
