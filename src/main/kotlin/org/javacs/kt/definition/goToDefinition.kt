package org.javacs.kt.definition

import org.javacs.kt.CompiledCode
import org.javacs.kt.position.findParent
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext

fun goToDefinition(code: CompiledCode): DeclarationDescriptor? {
    val expr = code.parsed.findElementAt(code.offset(0)) ?: return null
    val ref = expr.findParent<KtReferenceExpression>() ?: return null
    return code.compiled.get(BindingContext.REFERENCE_TARGET, ref)
}