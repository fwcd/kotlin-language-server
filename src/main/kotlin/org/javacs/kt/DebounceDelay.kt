package org.javacs.kt

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DebounceDelay(private val delay: Duration) {
    private val workerThread = Executors.newScheduledThreadPool(1)
    private var pendingTask = workerThread.submit({})

    fun submit(task: () -> Unit) {
        pendingTask.cancel(false)
        pendingTask = workerThread.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS)
    }

    fun waitForPendingTask() {
        pendingTask.get()
    }
}