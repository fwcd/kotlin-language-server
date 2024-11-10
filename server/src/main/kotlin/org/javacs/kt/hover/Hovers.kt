package org.javacs.kt.hover

import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Range
import com.intellij.psi.PsiDocCommentBase
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.renderer.RenderingFormat
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.javacs.kt.CompiledFile
import org.javacs.kt.completion.DECL_RENDERER
import org.javacs.kt.position.position
import org.javacs.kt.util.findParent
import org.javacs.kt.signaturehelp.getDocString
import org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI

fun hoverAt(file: CompiledFile, cursor: Int): Hover? {
    val (ref, target) = file.referenceAtPoint(cursor) ?: return typeHoverAt(file, cursor)
    val javaDoc = getDocString(file, cursor)
    val location = ref.textRange
    val hoverText = DECL_RENDERER.render(target)
    val hover = MarkupContent(
        "markdown", listOf("```kotlin\n$hoverText\n```", javaDoc).filter { it.isNotEmpty() }.joinToString("\n---\n")
    )
    val range = Range(
        position(file.content, location.startOffset), position(file.content, location.endOffset)
    )
    return Hover(hover, range)
}

private fun typeHoverAt(file: CompiledFile, cursor: Int): Hover? {
    val expression = file.parseAtPoint(cursor)?.findParent<KtExpression>() ?: return null
    val javaDoc: String =
        expression.children.mapNotNull { (it as? PsiDocCommentBase)?.text }.map(::renderJavaDoc).firstOrNull() ?: ""
    val scope = file.scopeAtPoint(cursor) ?: return null
    val hoverTextMaybe = file.bindingContextOf(expression, scope)?.let { renderTypeOf(expression, it) }
    val hoverText = hoverTextMaybe ?: return null
    val hover = MarkupContent(
        "markdown", listOf("```kotlin\n$hoverText\n```", javaDoc).filter { it.isNotEmpty() }.joinToString("\n---\n")
    )
    return Hover(hover)
}

// Source: https://github.com/JetBrains/kotlin/blob/master/idea/src/org/jetbrains/kotlin/idea/codeInsight/KotlinExpressionTypeProvider.kt
private val TYPE_RENDERER: DescriptorRenderer by lazy {
    DescriptorRenderer.COMPACT.withOptions {
        textFormat = RenderingFormat.PLAIN
        classifierNamePolicy = object : ClassifierNamePolicy {
            override fun renderClassifier(classifier: ClassifierDescriptor, renderer: DescriptorRenderer): String {
                if (DescriptorUtils.isAnonymousObject(classifier)) {
                    return "<anonymous object>"
                }
                return ClassifierNamePolicy.SHORT.renderClassifier(classifier, renderer)
            }
        }
    }
}

private fun renderJavaDoc(text: String): String {
    val split = text.split('\n')
    return split.mapIndexed { i, spl ->
        when (i) {
            0 -> spl.substring(spl.indexOf("/**") + 3) // get rid of the start comment characters
            split.size - 1 -> spl.substring(spl.indexOf("*/") + 2) // get rid of the end comment characters
            else -> spl.substring(spl.indexOf('*') + 1) // get rid of any leading *
        }
    }.joinToString("\n")
}

@OptIn(IDEAPluginsCompatibilityAPI::class)
private fun renderTypeOf(element: KtExpression, bindingContext: BindingContext): String? {
    if (element is KtCallableDeclaration) {
        val descriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, element]
        if (descriptor != null) {
            when (descriptor) {
                is CallableDescriptor -> return descriptor.returnType?.let(TYPE_RENDERER::renderType)
            }
        }
    }

    val expressionType =
        bindingContext[BindingContext.EXPRESSION_TYPE_INFO, element]?.type ?: element.getType(bindingContext)
    val result = expressionType?.let { TYPE_RENDERER.renderType(it) } ?: return null

    val smartCast = bindingContext[BindingContext.SMARTCAST, element]
    if (smartCast != null && element is KtReferenceExpression) {
        val declaredType = (bindingContext[BindingContext.REFERENCE_TARGET, element] as? CallableDescriptor)?.returnType
        if (declaredType != null) {
            return result + " (smart cast from " + TYPE_RENDERER.renderType(declaredType) + ")"
        }
    }
    return result
}
