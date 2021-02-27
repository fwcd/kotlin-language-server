package org.javacs.kt.progress

import java.io.Closeable
import java.util.concurrent.CompletableFuture

/** A facility for emitting progress notifications. */
interface Progress : Closeable {
    /**
     * Updates the progress percentage. The
     * value should be in the range [0, 100].
     */
    fun update(message: String? = null, percent: Int? = null)

    interface Factory {
        /**
         * Creates a new progress listener with
         * the given label. The label is intended
         * to be human-readable.
         */
        fun create(label: String): CompletableFuture<Progress>
    }
}
