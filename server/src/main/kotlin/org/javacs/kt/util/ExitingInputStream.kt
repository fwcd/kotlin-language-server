package org.javacs.kt.util

import java.io.InputStream
import org.javacs.kt.LOG

class ExitingInputStream(private val delegate: InputStream): InputStream() {
    override fun read(): Int = exitIfNegative { delegate.read() }

    override fun read(b: ByteArray): Int = exitIfNegative { delegate.read(b) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = exitIfNegative { delegate.read(b, off, len) }

    private fun exitIfNegative(call: () -> Int): Int {
        val result = call()

        if (result < 0) {
            LOG.info("System.in has closed, exiting")
            System.exit(0)
        }

        return result
    }
}
