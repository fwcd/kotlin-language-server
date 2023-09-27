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
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.smartcasts.getKotlinTypeForComparison
import org.jetbrains.kotlin.resolve.calls.util.getParentResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.error.ErrorType

private fun PsiElement.determineType(ctx: BindingContext): KotlinType? =
    when (this) {
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

private fun PsiElement.hintBuilder(kind: InlayHintKind, file: CompiledFile, hintLabel: String? = null): InlayHint? {
    val namedElement = ((this as? PsiNameIdentifierOwner)?.nameIdentifier ?: this)
    val range = range(file.parse.text, namedElement.textRange)
    val (pos, label) = when(kind) {
        InlayHintKind.Type -> {
            this.determineType(file.compile) ?.let {
                Pair(range.end, ": ${DECL_RENDERER.renderType(it)}")
            } ?: return null
        }
        InlayHintKind.Parameter -> Pair(range.start, "$hintLabel:")
    }
    val hint = InlayHint(pos, Either.forLeft(label))
    hint.kind = kind
    hint.paddingRight = true
    hint.paddingLeft = true
    return hint
}

private fun valueArgsToHints(
    callExpression: KtCallExpression,
    file: CompiledFile,
): List<InlayHint> {
    return callExpression.valueArguments.mapNotNull {
        val call = it.getParentResolvedCall(file.compile)
        val arg = (call?.getArgumentMapping(it) as ArgumentMatch).valueParameter.name
        it.hintBuilder(InlayHintKind.Parameter, file, arg.asString())
    }
}


fun provideHints(file: CompiledFile): List<InlayHint> {
    val hints = mutableListOf<InlayHint>()
    for (node in file.parse.preOrderTraversal().asIterable()) {
        when (node) {
            is KtCallExpression -> {
                hints.addAll(valueArgsToHints(node, file))
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
