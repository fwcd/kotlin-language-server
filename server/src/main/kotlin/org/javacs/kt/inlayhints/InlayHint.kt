package org.javacs.kt.inlayhints

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledFile
import org.javacs.kt.LOG
import org.javacs.kt.completion.DECL_RENDERER
import org.javacs.kt.position.range
import org.javacs.kt.util.preOrderTraversal
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.name.Name
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
import org.jetbrains.kotlin.resolve.calls.model.ResolvedValueArgument
import org.jetbrains.kotlin.resolve.calls.model.VarargValueArgument
import org.jetbrains.kotlin.resolve.calls.smartcasts.getKotlinTypeForComparison
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.error.ErrorType


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
                    "${(if (kind == InlayKind.TypeHint) ": " else "")}${DECL_RENDERER.renderType(it)}"
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
    val entries = resolvedCall?.valueArguments?.entries ?: return emptyList()

    return entries.mapNotNull { (t, u) ->
        val valueArg = u.arguments.firstOrNull()
        if (valueArg != null && !valueArg.isNamed()) {
            val label = getLabel(t.name, u)
            valueArg.asElement().hintBuilder(InlayKind.ParameterHint, file, label)
        } else null
    }
}

private fun getLabel(name: Name, arg: ResolvedValueArgument) =
    (name).let {
        when (arg) {
            is VarargValueArgument -> "...$it"
            else -> it.asString()
        }
    }

private fun lambdaValueParamsToHints(node: KtLambdaArgument, file: CompiledFile): List<InlayHint> {
    val params = node.getLambdaExpression()!!.valueParameters

    //hint should not be rendered when parameter is of type DestructuringDeclaration
    //example: Map.forEach { (k,v) -> _ }
    //lambda parameter (k,v) becomes (k :hint, v :hint) :hint <- outer hint isnt needed
    params.singleOrNull()?.let {
        if (it.destructuringDeclaration != null) return emptyList()
    }

    return params.mapNotNull {
        it.hintBuilder(InlayKind.TypeHint, file)
    }
}

private fun chainedMethodsHints(node: KtDotQualifiedExpression, file: CompiledFile): List<InlayHint> {
        return node.getChildrenOfType<KtCallExpression>().mapNotNull {
            it.hintBuilder(InlayKind.ChainingHint, file)
        }
}

private fun destructuringVarHints(
    node: KtDestructuringDeclaration,
    file: CompiledFile
): List<InlayHint> {
    return node.entries.mapNotNull {  it.hintBuilder(InlayKind.TypeHint, file) }
}

private fun declarationHint(node: KtProperty, file: CompiledFile): InlayHint? {
    //check decleration does not include type i.e. var t1: String
    return if (node.typeReference == null) {
        node.hintBuilder(InlayKind.TypeHint, file)
    } else null
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
                val next = (node.nextSibling as? PsiWhiteSpace)
                val nextSiblingElement = next?.nextSibling?.node?.elementType

                if (nextSiblingElement != null && nextSiblingElement == DOT) {
                   hints.addAll(chainedMethodsHints(node, file))
                }
            }
            is KtCallExpression -> {
                //hints are not rendered for argument of type lambda expression i.e. list.map { it }
                if (node.getChildOfType<KtLambdaArgument>() == null) {
                    hints.addAll(callableArgsToHints(node, file))
                }
            }
            is KtDestructuringDeclaration -> {
                hints.addAll(destructuringVarHints(node, file))
            }
            is KtProperty -> declarationHint(node, file)?.let { hints.add(it) }
        }
    }
    return hints
}

enum class InlayKind(val base: InlayHintKind) {
    TypeHint(InlayHintKind.Type),
    ParameterHint(InlayHintKind.Parameter),
    ChainingHint(InlayHintKind.Type),
}
