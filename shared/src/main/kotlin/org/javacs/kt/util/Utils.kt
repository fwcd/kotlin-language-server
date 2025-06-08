package org.javacs.kt.util

import org.javacs.kt.LOG
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths

fun execAndReadStdoutAndStderr(shellCommand: List<String>, directory: Path): Pair<String, String> {
    val process = ProcessBuilder(shellCommand).directory(directory.toFile()).start()
    val stdout = process.inputStream
    val stderr = process.errorStream
    var output = ""
    var errors = ""
    val outputThread = Thread { stdout.bufferedReader().use { output += it.readText() } }
    val errorsThread = Thread { stderr.bufferedReader().use { errors += it.readText() } }
    outputThread.start()
    errorsThread.start()
    outputThread.join()
    errorsThread.join()
    return Pair(output, errors)
}

inline fun withCustomStdout(delegateOut: PrintStream, task: () -> Unit) {
    val actualOut = System.out
    System.setOut(delegateOut)
    task()
    System.setOut(actualOut)
}

fun winCompatiblePathOf(path: String): Path =
    if (path.get(2) == ':' && path.get(0) == '/') {
        // Strip leading '/' when dealing with paths on Windows
        Paths.get(path.substring(1))
    } else {
        Paths.get(path)
    }

fun String.partitionAroundLast(separator: String): Pair<String, String> = lastIndexOf(separator)
    .let { Pair(substring(0, it), substring(it, length)) }

fun Path.replaceExtensionWith(newExtension: String): Path {
	val oldName = fileName.toString()
	val newName = oldName.substring(0, oldName.lastIndexOf(".")) + newExtension
	return resolveSibling(newName)
}

inline fun <T, C : Iterable<T>> C.onEachIndexed(transform: (index: Int, T) -> Unit): C = apply {
    for ((i, element) in this.withIndex()) {
        transform(i, element)
    }
}

fun <T> noResult(message: String, result: T): T {
    LOG.info(message)
    return result
}

fun <T> emptyResult(message: String): List<T> = noResult(message, emptyList())

fun <T> nullResult(message: String): T? = noResult(message, null)

inline fun <T> tryResolving(what: String, resolver: () -> T?): T? {
    try {
        val resolved = resolver()
        if (resolved != null) {
            LOG.info("Successfully resolved {} to {}", what, resolved)
            return resolved
        } else {
            LOG.info("Could not resolve {} as it is null", what)
        }
    } catch (e: Exception) {
        LOG.info("Could not resolve {}: {}", what, e.message)
    }
    return null
}
