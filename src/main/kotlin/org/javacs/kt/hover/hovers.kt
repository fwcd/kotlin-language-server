package org.javacs.kt.hover

import com.intellij.openapi.util.TextRange
import org.javacs.kt.CompiledCode
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

fun hovers(code: CompiledCode): Pair<TextRange, DeclarationDescriptor>? {
    val psi = code.exprAt(0) ?: return null
    val stopAtDeclaration = psi.parentsWithSelf.takeWhile { it !is KtDeclaration }
    val reference = stopAtDeclaration.filterIsInstance<KtReferenceExpression>().firstOrNull() ?: return null
    val hover = code.referenceTarget(reference) ?: return null
    val range = reference.textRange.shiftRight(code.textOffset)

    return Pair(range, hover)
}