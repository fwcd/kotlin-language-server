package org.javacs.kt.util

import org.javacs.kt.LOG
import java.time.Duration
import java.util.function.Supplier
import java.util.concurrent.atomic.AtomicReference
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

    fun submitImmediately(task: (cancelCallback : () -> Boolean) -> Unit) {
        pendingTask?.cancel(false)
        val currentTaskRef = AtomicReference<Future<*>>()
        val currentTask = executor.submit({ -> task.invoke({ -> val f = currentTaskRef.get(); f?.isCancelled()?:false})})
        currentTaskRef.set(currentTask);
        pendingTask = currentTask
    }

    fun submit(task: () -> Unit) {
        pendingTask?.cancel(false)
        pendingTask = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS)
    }

    fun submit(task: (cancelCallback : () -> Boolean) -> Unit) {
        pendingTask?.cancel(false)
        val currentTaskRef = AtomicReference<Future<*>>()
        val currentTask = executor.schedule({ -> task.invoke({ -> val f = currentTaskRef.get(); f?.isCancelled()?:false})}, delayMs, TimeUnit.MILLISECONDS)
        currentTaskRef.set(currentTask);
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
