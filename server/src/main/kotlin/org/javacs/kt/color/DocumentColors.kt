package org.javacs.kt.color

import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.ColorInformation
import org.javacs.kt.LOG
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.range
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.KtNodeTypes
import com.intellij.psi.PsiElement

/**
 * A class that can represent RGB(A) colors.
 *
 * Supported classes should have a `Color(r, g, b)`
 * and/or a `Color(r, g, b, a)` constructor using
 * ints in the range [0, 255] or doubles within the
 * range [0, 1].
 */
private val COLOR_CLASSES = listOf(
    "java.awt.Color",
    "org.eclipse.lsp4j.Color"
)

fun documentColors(file: CompiledFile): List<ColorInformation> =
    doDocumentColors(file.parse, file)

fun doDocumentColors(element: PsiElement, file: CompiledFile): List<ColorInformation> = when (element) {
    is KtCallExpression -> listOfNotNull(colorInformation(element, file))
    else -> element.children.flatMap { doDocumentColors(it, file) }
}

private fun colorInformation(call: KtCallExpression, file: CompiledFile): ColorInformation? {
    val callee = call.calleeExpression as? KtReferenceExpression ?: return null
    val scope = file.scopeAtPoint(call.startOffset) ?: return null
    val type = file.typeOfExpression(call, scope) ?: return null
    val fqName = type.constructor.declarationDescriptor?.fqNameSafe ?: return null

    // Make sure the name is a color and that the call is a constructor call
    if (fqName.toString() !in COLOR_CLASSES || fqName.shortName().toString() != callee.text) return null

    val args = call.valueArguments
    val components = args
        .mapNotNull { it.getArgumentExpression() }
        .mapNotNull { it as? KtConstantExpression }
        .mapNotNull(::colorComponent)

    if (args.size != components.size) return null

    return when (args.size) {
        // Color(r, g, b) | Color(r, g, b, a)
        3, 4 -> ColorInformation(
            range(call),
            Color(components[0].value, components[1].value, components[2].value, components.getOrNull(3)?.value ?: 1.0)
        )
        else -> null
    }
}

private data class ColorComponent(val value: Double, val isDoubleInSource: Boolean)

private fun colorComponent(arg: KtConstantExpression): ColorComponent? = when (arg.elementType) {
    KtNodeTypes.INTEGER_CONSTANT -> arg.text?.toInt()?.let { ColorComponent(it.toDouble() / 255.0, isDoubleInSource = false) }
    KtNodeTypes.FLOAT_CONSTANT -> arg.text?.toDouble()?.let { ColorComponent(it, isDoubleInSource = true) }
    else -> null
}
