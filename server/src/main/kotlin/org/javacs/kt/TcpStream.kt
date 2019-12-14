package org.javacs.kt

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket

/**
 * Starts a TCP server socket. Blocks until the first
 * client has connected, then returns a pair of IO streams.
 */
fun tcpConnectToClient(port: Int): Pair<InputStream, OutputStream> =
    ServerSocket(port).accept().let { Pair(it.inputStream, it.outputStream) }
