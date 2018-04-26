package org.javacs.kt.hover

import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkedString
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledFile
import org.javacs.kt.completion.DECL_RENDERER
import org.javacs.kt.position.position

fun hoverAt(file: CompiledFile, cursor: Int): Hover? {
    val (ref, target) = file.referenceAtPoint(cursor) ?: return null
    val location = ref.textRange
    val hoverText = DECL_RENDERER.render(target)
    val hover = Either.forRight<String, MarkedString>(MarkedString("kotlin", hoverText))
    val range = Range(
            position(file.content, location.startOffset),
            position(file.content, location.endOffset))
    return Hover(listOf(hover), range)
}