package org.javacs.kt

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import java.util.concurrent.Executors
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.ConfigurationItem
import org.javacs.kt.util.ExitingInputStream

class Args {
    @Parameter(names = ["--tcpPort", "-p"])
    var tcpPort: Int? = null
}

fun main(argv: Array<String>) {
    // Redirect java.util.logging calls (e.g. from LSP4J)
    LOG.connectJULFrontend()

    val args = Args().also { JCommander.newBuilder().addObject(it).build().parse(*argv) }
    val (inStream, outStream) = args.tcpPort?.let { tcpConnectToClient(it) } ?: Pair(System.`in`, System.out)

    val server = KotlinLanguageServer()
    val threads = Executors.newSingleThreadExecutor { Thread(it, "client") }
    val launcher = LSPLauncher.createServerLauncher(server, ExitingInputStream(inStream), outStream, threads, { it })

    server.connect(launcher.remoteProxy)
    launcher.startListening()
}
