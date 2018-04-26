package org.javacs.kt.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.javacs.kt.LOG
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.experimental.buildSequence

inline fun<reified Find> PsiElement.findParent() =
        this.parentsWithSelf.filterIsInstance<Find>().firstOrNull()

fun PsiElement.preOrderTraversal(): Sequence<PsiElement> {
    val root = this

    return buildSequence {
        yield(root)

        for (child in root.children) {
            yieldAll(child.preOrderTraversal())
        }
    }
}

fun PsiFile.toPath() =
        Paths.get(this.originalFile.viewProvider.virtualFile.path)

fun <T> noResult(message: String, result: T): T {
    LOG.info(message)

    return result
}

fun <T> noFuture(message: String, contents: T): CompletableFuture<T> = noResult(message, CompletableFuture.completedFuture(contents))

fun <T> emptyResult(message: String): List<T> = noResult(message, emptyList())

fun <T> nullResult(message: String): T? = noResult(message, null)