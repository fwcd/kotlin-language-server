package org.javacs.kt.definition

import com.intellij.openapi.util.TextRange
import org.javacs.kt.CompiledCode
import org.javacs.kt.diagnostic.toPath
import org.javacs.kt.position.findParent
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtReferenceExpression
import java.nio.file.Path

fun goToDefinition(code: CompiledCode): Pair<Path, TextRange>? {
    val expr = code.exprAt(0) ?: return null
    val ref = expr.findParent<KtReferenceExpression>() ?: return null
    val declaration = code.referenceTarget(ref) ?: return null
    val target = declaration.findPsi() ?: return null

    return Pair(target.containingFile.toPath(), target.textRange)
}