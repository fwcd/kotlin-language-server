package org.javacs.kt.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.experimental.buildSequence

private val LOG = LoggerFactory.getLogger("org.javacs.kt.util.UtilsKt")

inline fun<reified Find> PsiElement.findParent() =
        this.parentsWithSelf.filterIsInstance<Find>().firstOrNull()

fun execAndReadStdout(shellCommand: String, directory: Path): String {
    val process = Runtime.getRuntime().exec(shellCommand, null, directory.toFile())
    val stdout = process.inputStream
    process.waitFor()
    var result = ""
    stdout.bufferedReader().use {
        result = it.readText()
    }
    return result
}

fun PsiElement.preOrderTraversal(): Sequence<PsiElement> {
    val root = this

    return buildSequence {
        yield(root)

        for (child in root.children) {
            yieldAll(child.preOrderTraversal())
        }
    }
}

fun winCompatiblePathOf(path: String): Path {
    if (path.get(2) == ':' && path.get(0) == '/') {
        // Strip leading '/' when dealing with paths on Windows
        return Paths.get(path.substring(1))
    } else {
        return Paths.get(path)
    }
}

fun PsiFile.toPath(): Path =
        winCompatiblePathOf(this.originalFile.viewProvider.virtualFile.path)

fun <T> noResult(message: String, result: T): T {
    LOG.info(message)
    return result
}

fun <T> noFuture(message: String, contents: T): CompletableFuture<T> = noResult(message, CompletableFuture.completedFuture(contents))

fun <T> emptyResult(message: String): List<T> = noResult(message, emptyList())

fun <T> nullResult(message: String): T? = noResult(message, null)

public class KotlinLSException: RuntimeException {
	constructor(msg: String) : super(msg) {}

	constructor(msg: String, cause: Throwable) : super(msg, cause) {}
}

fun <T> firstNonNull(vararg optionals: () -> T?): T? {
    for (optional in optionals) {
        val result = optional()
        if (result != null) {
            return result
        }
    }
    return null
}

fun <T> tryResolving(what: String, resolver: () -> T?): T? {
    try {
        val resolved = resolver()
        if (resolved != null) {
            LOG.info("Successfully resolved {}", what)
            return resolved
        }
    } catch (e: Exception) {
        LOG.info("Could not resolve {} due to {}", what, e.message)
    }
    return null
}
