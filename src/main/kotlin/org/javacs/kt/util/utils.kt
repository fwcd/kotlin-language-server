package org.javacs.kt.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.javacs.kt.LOG
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.experimental.buildSequence

inline fun<reified Find> PsiElement.findParent() =
        this.parentsWithSelf.filterIsInstance<Find>().firstOrNull()

fun execAndReadStdout(shellCommand: String, directory: Path): String {
    val process = Runtime.getRuntime().exec(shellCommand, null, directory.toFile())
    val stdout = process.inputStream
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

fun Path.replaceExtensionWith(newExtension: String): Path {
	val oldName = fileName.toString()
	val newName = oldName.substring(0, oldName.lastIndexOf(".")) + newExtension
	return resolveSibling(newName)
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

fun <T> firstNonNull(vararg optionals: () -> T?): T? {
    for (optional in optionals) {
        val result = optional()
        if (result != null) {
            return result
        }
    }
    return null
}

fun <T> nonNull(item: T?, errorMsgIfNull: String): T =
    if (item == null) {
        throw NullPointerException(errorMsgIfNull)
    } else item

fun <T> tryResolving(what: String, resolver: () -> T?): T? {
    try {
        val resolved = resolver()
        if (resolved != null) {
            LOG.info("Successfully resolved $what")
            return resolved
        } else {
            LOG.info("Could not resolve $what as it is null")
        }
    } catch (e: Exception) {
        LOG.info("Could not resolve $what: ${e.message}")
    }
    return null
}
