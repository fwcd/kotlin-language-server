package org.javacs.kt.definition

import org.eclipse.lsp4j.Location
import org.javacs.kt.CompiledCode
import org.javacs.kt.position.findParent
import org.javacs.kt.position.location
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.BindingContext

fun goToDefinition(code: CompiledCode): Location? {
    val declaration = doGoToDefinition(code) ?: return null
    return location(declaration)
}

private fun doGoToDefinition(code: CompiledCode): DeclarationDescriptor? {
    val expr = code.parsed.findElementAt(code.offset(0)) ?: return null
    val ref = expr.findParent<KtReferenceExpression>() ?: return null
    return code.compiled.get(BindingContext.REFERENCE_TARGET, ref)
}