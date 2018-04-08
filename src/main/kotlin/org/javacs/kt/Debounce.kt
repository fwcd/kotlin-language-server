package org.javacs.kt

import com.google.common.util.concurrent.RateLimiter
import java.util.concurrent.Executors

class Debounce(permitsPerSecond: Double) {
    private val rateLimit = RateLimiter.create(permitsPerSecond)
    private val workerThread = Executors.newSingleThreadExecutor()
    private var pendingTask = workerThread.submit({})

    fun submit(task: () -> Unit) {
        pendingTask.cancel(false)
        pendingTask = workerThread.submit {
            rateLimit.acquire()
            task()
        }
    }
}