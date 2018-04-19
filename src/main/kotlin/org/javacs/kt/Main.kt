package org.javacs.kt

import org.eclipse.lsp4j.launch.LSPLauncher

fun main(args: Array<String>) {
    val server = KotlinLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(server, System.`in`, System.out)
    server.connect(launcher.remoteProxy)
    launcher.startListening()
}

class Example {
    fun foo() {
        val x = 1.0f
        val y = 1L
        val sealed = 1
        val data = 2
        val public = 3
        val extension = 1
    }

    fun bar() {
        this@Example.foo()
    }
}

fun (String).bar() {

}