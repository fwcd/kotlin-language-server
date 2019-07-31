package org.javacs.kt.util

import java.time.Duration
import java.util.function.Supplier
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

private var threadCount = 0

class Debouncer(
    private val delay: Duration,
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1) {
        Thread(it, "debounce${threadCount++}")
    }
) {
    private val delayMs = delay.toMillis()
    private var pendingTask: Future<*>? = null

    fun submitImmediately(task: () -> Unit) {
        pendingTask?.cancel(false)
        pendingTask = executor.submit(task)
    }

    fun submit(task: () -> Unit) {
        pendingTask?.cancel(false)
        pendingTask = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS)
    }

    fun waitForPendingTask() {
        pendingTask?.get()
    }
}
