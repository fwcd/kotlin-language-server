package org.javacs.kt

import java.io.InputStream
import java.util.concurrent.Executors
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.eclipse.lsp4j.launch.LSPLauncher

private val LOG = LoggerFactory.getLogger("org.javacs.kt.MainKt")

fun main(args: Array<String>) {
    val server = KotlinLanguageServer()
    val input = ExitOnClose(System.`in`)
    val threads = Executors.newSingleThreadExecutor({ Thread(it, "client")})
    val launcher = LSPLauncher.createServerLauncher(server, input, System.out, threads, { it })
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
