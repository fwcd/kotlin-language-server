package org.javacs.kt.util

import org.javacs.kt.LOG
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future

private var threadCount = 0

class Debouncer(
    delay: Duration,
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1) {
        Thread(it, "debounce${threadCount++}")
    }
) {
    private val delayMs = delay.toMillis()
    private var pendingTask: Future<*>? = null

    fun submitImmediately(task: (cancelCallback: () -> Boolean) -> Unit) {
        pendingTask?.cancel(false)
        val currentTaskRef = AtomicReference<Future<*>>()
        val currentTask = executor.submit { task { currentTaskRef.get()?.isCancelled ?: false } }
        currentTaskRef.set(currentTask)
        pendingTask = currentTask
    }

    fun schedule(task: (cancelCallback: () -> Boolean) -> Unit) {
        pendingTask?.cancel(false)
        val currentTaskRef = AtomicReference<Future<*>>()
        val currentTask = executor.schedule({ task { currentTaskRef.get()?.isCancelled ?: false } }, delayMs, TimeUnit.MILLISECONDS)
        currentTaskRef.set(currentTask)
        pendingTask = currentTask
    }

    fun waitForPendingTask() {
        pendingTask?.get()
    }

    fun shutdown(awaitTermination: Boolean) {
        executor.shutdown()
        if (awaitTermination) {
			LOG.info("Awaiting debouncer termination...")
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
		}
    }
}
