package org.javacs.kt.util

import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.nio.file.Path

inline fun<reified Find> PsiElement.findParent() =
        this.parentsWithSelf.filterIsInstance<Find>().firstOrNull()

fun PsiElement.preOrderTraversal(shouldTraverse: (PsiElement) -> Boolean = { true }): Sequence<PsiElement> {
    val root = this

    return sequence {
        if (shouldTraverse(root)) {
            yield(root)

            for (child in root.children) {
                if (shouldTraverse(child)) {
                    yieldAll(child.preOrderTraversal(shouldTraverse))
                }
            }
        }
    }
}

fun PsiFile.toPath(): Path =
        winCompatiblePathOf(this.originalFile.viewProvider.virtualFile.path)
