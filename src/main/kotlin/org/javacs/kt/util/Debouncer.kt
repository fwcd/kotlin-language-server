package org.javacs.kt.util

import java.time.Duration
import java.util.function.Supplier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture

private var threadCount = 0

class Debouncer(private val delay: Duration) {
    private val workerThread = Executors.newScheduledThreadPool(1) { Thread(it, "debounce-${threadCount++}") }
    private var pendingTask = workerThread.submit({})

    fun submitImmediately(task: () -> Unit) {
        pendingTask.cancel(false)
        pendingTask = workerThread.submit(task)
    }

    fun submit(task: () -> Unit) {
        pendingTask.cancel(false)
        pendingTask = workerThread.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS)
    }

    fun waitForPendingTask() {
        pendingTask.get()
    }
}