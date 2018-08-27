package org.javacs.kt

import java.io.InputStream
import java.util.concurrent.Executors
import org.eclipse.lsp4j.launch.LSPLauncher

fun main(args: Array<String>) {
    // Redirect java.util.logging calls (e.g. from LSP4J)
    LOG.connectJULFrontend()
    
    val server = KotlinLanguageServer()
    val input = ExitOnClose(System.`in`)
    val threads = Executors.newSingleThreadExecutor { Thread(it, "client") }
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
