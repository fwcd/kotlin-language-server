package org.javacs.kt.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.nio.file.Paths
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