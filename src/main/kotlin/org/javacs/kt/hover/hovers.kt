package org.javacs.kt.hover

import com.intellij.openapi.util.TextRange
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkedString
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledCode
import org.javacs.kt.completion.DECL_RENDERER
import org.javacs.kt.position.position
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext

fun hoverAt(code: CompiledCode): Hover? {
    val (location, decl) = doHoverAt(code) ?: return null
    val hoverText = DECL_RENDERER.render(decl)
    val hover = Either.forRight<String, MarkedString>(MarkedString("kotlin", hoverText))
    val range = Range(
            position(code.content, location.startOffset),
            position(code.content, location.endOffset))
    return Hover(listOf(hover), range)
}

private fun doHoverAt(code: CompiledCode): Pair<TextRange, DeclarationDescriptor>? {
    val psi = code.parsed.findElementAt(code.offset(0)) ?: return null
    val stopAtDeclaration = psi.parentsWithSelf.takeWhile { it !is KtDeclaration }
    val reference = stopAtDeclaration.filterIsInstance<KtReferenceExpression>().firstOrNull() ?: return null
    val hover = code.compiled.get(BindingContext.REFERENCE_TARGET, reference) ?: return null
    val range = reference.textRange.shiftRight(code.textOffset)

    return Pair(range, hover)
}