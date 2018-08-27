package org.javacs.kt

import java.util.concurrent.Executors
import org.eclipse.lsp4j.launch.LSPLauncher
import org.javacs.kt.util.ExitingInputStream

fun main(args: Array<String>) {
    // Redirect java.util.logging calls (e.g. from LSP4J)
    LOG.connectJULFrontend()
    
    val server = KotlinLanguageServer()
    val input = ExitingInputStream(System.`in`)
    val threads = Executors.newSingleThreadExecutor { Thread(it, "client") }
    val launcher = LSPLauncher.createServerLauncher(server, input, System.out, threads, { it })
    
    server.connect(launcher.remoteProxy)
    launcher.startListening()
}
