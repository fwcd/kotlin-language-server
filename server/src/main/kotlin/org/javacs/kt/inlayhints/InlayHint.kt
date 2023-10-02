package org.javacs.kt.inlayhints

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.range
import org.javacs.kt.util.preOrderTraversal
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.VarargValueArgument
import org.jetbrains.kotlin.resolve.calls.smartcasts.getKotlinTypeForComparison
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.error.ErrorType


enum class InlayKind(val base: InlayHintKind) {
    TypeHint(InlayHintKind.Type),
    ParameterHint(InlayHintKind.Parameter),
    ChainingHint(InlayHintKind.Type),
}

private fun PsiElement.determineType(ctx: BindingContext): KotlinType? =
    when (this) {
        is KtCallExpression -> {
            this.getKotlinTypeForComparison(ctx)
        }
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

private fun PsiElement.hintBuilder(kind: InlayKind, file: CompiledFile, label: String? = null): InlayHint? {
    val namedElement = ((this as? PsiNameIdentifierOwner)?.nameIdentifier ?: this)
    val range = range(file.parse.text, namedElement.textRange)

    val hint = when(kind) {
        InlayKind.ParameterHint -> InlayHint(range.start, Either.forLeft("$label:"))
        else ->
            this.determineType(file.compile) ?.let {
                InlayHint(range.end, Either.forLeft(
                    "${(kind == InlayKind.TypeHint).let { ":" }} $it"
                ))
            } ?: return null
    }
    hint.kind = kind.base
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
        if (u.arguments.isNotEmpty()) {
            val valueArg = u.arguments.first()

            if (!valueArg.isNamed()) {
                val label = (t.name).let { name ->
                    when (u) {
                        is VarargValueArgument -> "...$name"
                        else -> name.asString()
                    }
                }
                valueArg.asElement().hintBuilder(InlayKind.ParameterHint, file, label)?.let { hints.add(it) }
            }

        }
    }
    return hints
}

private fun lambdaValueParamsToHints(node: KtLambdaArgument, file: CompiledFile): List<InlayHint> {
    return node.getLambdaExpression()!!.valueParameters.mapNotNull {
        it.hintBuilder(InlayKind.TypeHint, file)
    }
}

private fun chainedMethodsHints(node: KtDotQualifiedExpression, file: CompiledFile): List<InlayHint> {
        return node.getChildrenOfType<KtCallExpression>().mapNotNull {
            it.hintBuilder(InlayKind.ChainingHint, file)
        }
}

fun provideHints(file: CompiledFile): List<InlayHint> {
    val hints = mutableListOf<InlayHint>()
    for (node in file.parse.preOrderTraversal().asIterable()) {
        when (node) {
            is KtLambdaArgument -> {
                hints.addAll(lambdaValueParamsToHints(node, file))
            }
            is KtDotQualifiedExpression -> {
                ///chaining is defined as an expression whose next sibling tokens are newline and dot
                (node.nextSibling as? PsiWhiteSpace)?.let {
                    if (it.nextSibling.node.elementType == DOT) {
                       hints.addAll(chainedMethodsHints(node, file))
                    }
                }
            }
            is KtCallExpression -> {
                //hints are not rendered for argument of type lambda expression i.e. list.map { it }
                if (node.getChildOfType<KtLambdaArgument>() == null) {
                    hints.addAll(callableArgsToHints(node, file))
                }
            }
            is KtDestructuringDeclaration -> {
                hints.addAll(node.entries.mapNotNull {  it.hintBuilder(InlayKind.TypeHint, file) })
            }
            is KtProperty -> {
                //check decleration does not include type i.e. var t1: String
                if (node.typeReference == null) {
                    node.hintBuilder(InlayKind.TypeHint, file)?.let { hints.add(it) }
                }
            }
        }
    }
    return hints
}
