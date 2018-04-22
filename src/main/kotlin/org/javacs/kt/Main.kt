package org.javacs.kt

import java.io.InputStream
import org.eclipse.lsp4j.launch.LSPLauncher

fun main(args: Array<String>) {
    val server = KotlinLanguageServer()
    val input = ExitOnClose(System.`in`)
    val launcher = LSPLauncher.createServerLauncher(server, input, System.out)
    server.connect(launcher.remoteProxy)
    launcher.startListening()
}

private class ExitOnClose(private val delegate: InputStream): InputStream() {
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