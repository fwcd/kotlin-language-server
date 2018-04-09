package org.javacs.kt.definition

import org.javacs.kt.CompiledCode
import org.javacs.kt.position.findParent
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtReferenceExpression

fun goToDefinition(code: CompiledCode): DeclarationDescriptor? {
    val expr = code.exprAt(0) ?: return null
    val ref = expr.findParent<KtReferenceExpression>() ?: return null
    return code.referenceTarget(ref)
}