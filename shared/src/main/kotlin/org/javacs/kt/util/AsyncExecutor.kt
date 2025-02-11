package org.javacs.kt.util

import org.javacs.kt.LOG
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AsyncExecutor(private val name: String) {
    private val workerThread = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "kls-executor-${name}").apply {
            isDaemon = true
        }
    }

    /**
     * Executes a task asynchronously without returning a result
     */
    fun execute(task: () -> Unit): CompletableFuture<Void> =
        CompletableFuture.runAsync({ task() }, workerThread)

    /**
     * Executes a task asynchronously and returns a result
     */
    fun <R> compute(task: () -> R): CompletableFuture<R> =
        CompletableFuture.supplyAsync({ task() }, workerThread)

    /**
     * Shuts down the executor
     * @param awaitTermination if true, waits for all tasks to complete
     */
    fun shutdown(awaitTermination: Boolean) {
        workerThread.shutdown()
        if (awaitTermination) {
            LOG.info {"Awaiting async termination of kls executor..."}
            workerThread.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
        }
    }

    /**
     * Attempts to shut down executor immediately, cancelling running tasks
     */
    fun shutdownNow(): List<Runnable> = workerThread.shutdownNow()
}
