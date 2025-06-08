package org.javacs.kt.progress

import java.io.Closeable
import java.util.concurrent.CompletableFuture

/** A facility for emitting progress notifications. */
interface Progress : Closeable {
    /** Updates the progress percentage. The value should be in the range [0, 100]. */
    fun update(message: String? = null, percent: Int? = null)

    object None : Progress {
        override fun update(message: String?, percent: Int?) {
            // Do nothing
        }

        override fun close() {
            // Do nothing
        }
    }

    /**
     * Creates a new progress listener with the given label. The label is intended to be
     * human-readable.
     */
    fun interface Factory {
        fun create(label: String): CompletableFuture<Progress>

        companion object {
            val None: Factory = Factory { CompletableFuture.completedFuture(Progress.None) }
        }
    }
}
