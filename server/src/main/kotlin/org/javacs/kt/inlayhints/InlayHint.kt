package org.javacs.kt.inlayhints

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledFile
import org.javacs.kt.completion.DECL_RENDERER
import org.javacs.kt.position.range
import org.javacs.kt.util.preOrderTraversal
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.VarargValueArgument
import org.jetbrains.kotlin.resolve.calls.smartcasts.getKotlinTypeForComparison
import org.jetbrains.kotlin.resolve.calls.util.*
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.error.ErrorType

private fun PsiElement.determineType(ctx: BindingContext): KotlinType? =
    when (this) {
        is KtParameter -> {
            if (this.isLambdaParameter and (this.typeReference == null)) {
                val descriptor = ctx[BindingContext.DECLARATION_TO_DESCRIPTOR, this] as CallableDescriptor
                descriptor.returnType
            } else null
        }
        is KtDestructuringDeclarationEntry -> {
            val resolvedCall = ctx[BindingContext.COMPONENT_RESOLVED_CALL, this]
            resolvedCall?.resultingDescriptor?.returnType
        }
        is KtProperty -> {
            //TODO: better handling for unresolved-type error
            val type = this.getKotlinTypeForComparison(ctx)
            if (type is ErrorType) null else type
        }
        else -> null
    }

private fun PsiElement.hintBuilder(kind: InlayHintKind, file: CompiledFile, label: String? = null): InlayHint? {
    val namedElement = ((this as? PsiNameIdentifierOwner)?.nameIdentifier ?: this)
    val range = range(file.parse.text, namedElement.textRange)
    val hint = when(kind) {
        InlayHintKind.Type ->
            this.determineType(file.compile) ?.let {
                InlayHint(range.end, Either.forLeft(": ${DECL_RENDERER.renderType(it)}"))
            } ?: return null
        InlayHintKind.Parameter -> InlayHint(range.start, Either.forLeft("$label:"))
    }
    hint.kind = kind
    hint.paddingRight = true
    hint.paddingLeft = true
    return hint
}

private fun callableArgsToHints(
    callExpression: KtCallExpression,
    file: CompiledFile,
): List<InlayHint> {
    val resolvedCall = callExpression.getResolvedCall(file.compile)

    val hints = mutableListOf<InlayHint>()
    resolvedCall?.valueArguments?.forEach { (t, u) ->
        val valueArg = u.arguments.first()

        if (!valueArg.isNamed()) {
            val label = (t.name).let { name ->
                when (u) {
                    is VarargValueArgument -> "...$name"
                    else -> name.asString()
                }
            }
            valueArg.asElement().hintBuilder(InlayHintKind.Parameter, file, label)?.let { hints.add(it) }
        }
    }
    return hints
}

private fun lambdaValueParamsToHints(node: KtLambdaArgument, file: CompiledFile): List<InlayHint> {
    return node.getLambdaExpression()!!.valueParameters.mapNotNull {
        it.hintBuilder(InlayHintKind.Type, file)
    }
}

fun provideHints(file: CompiledFile): List<InlayHint> {
    val hints = mutableListOf<InlayHint>()
    for (node in file.parse.preOrderTraversal().asIterable()) {
        when (node) {
            is KtLambdaArgument -> {
                hints.addAll(lambdaValueParamsToHints(node, file))
            }
            is KtCallExpression -> {
                //hints are not rendered for argument of type lambda expression i.e. list.map { it }
                if (node.getChildOfType<KtLambdaArgument>() == null) {
                    hints.addAll(callableArgsToHints(node, file))
                }
            }
            is KtDestructuringDeclaration -> {
                hints.addAll(node.entries.mapNotNull {  it.hintBuilder(InlayHintKind.Type, file) })
            }
            is KtProperty -> {
                //check decleration does not include type i.e. var t1: String
                if (node.typeReference == null) {
                    node.hintBuilder(InlayHintKind.Type, file)?.let { hints.add(it) }
                }
            }
        }
    }
    return hints
}
