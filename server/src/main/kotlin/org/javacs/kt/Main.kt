package org.javacs.kt

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import java.util.concurrent.Executors
import org.eclipse.lsp4j.launch.LSPLauncher
import org.javacs.kt.util.ExitingInputStream
import org.javacs.kt.util.tcpStartServer
import org.javacs.kt.util.tcpConnectToClient

class Args {
    /*
     * The language server can currently be launched in three modes:
     *  - Stdio, in which case no argument should be specified (used by default)
     *  - TCP Server, in which case the client has to connect to the specified tcpServerPort (used by the Docker image)
     *  - TCP Client, in whcih case the server will connect to the specified tcpClientPort/tcpClientHost (optionally used by VSCode)
     */

    @Parameter(names = ["--tcpServerPort", "-sp"])
    var tcpServerPort: Int? = null
    @Parameter(names = ["--tcpClientPort", "-p"])
    var tcpClientPort: Int? = null
    @Parameter(names = ["--tcpClientHost", "-h"])
    var tcpClientHost: String = "localhost"
}

fun main(argv: Array<String>) {
    // Redirect java.util.logging calls (e.g. from LSP4J)
    LOG.connectJULFrontend()

    val args = Args().also { JCommander.newBuilder().addObject(it).build().parse(*argv) }
    val (inStream, outStream) = args.tcpClientPort?.let {
        // Launch as TCP Client
        LOG.connectStdioBackend()
        tcpConnectToClient(args.tcpClientHost, it)
    } ?: args.tcpServerPort?.let {
        // Launch as TCP Server
        LOG.connectStdioBackend()
        tcpStartServer(it)
    } ?: Pair(System.`in`, System.out)

    val server = KotlinLanguageServer()
    val threads = Executors.newSingleThreadExecutor { Thread(it, "client") }
    val launcher = LSPLauncher.createServerLauncher(server, ExitingInputStream(inStream), outStream, threads) { it }

    server.connect(launcher.remoteProxy)
    launcher.startListening()
}
