package org.javacs.kt.signatureHelp

import org.javacs.kt.CompiledCode
import org.javacs.kt.completion.identifierOverloads
import org.javacs.kt.completion.memberOverloads
import org.javacs.kt.position.findParent
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

class SignatureHelpSession(private val code: CompiledCode) {
    fun signatureHelp(): KotlinSignatureHelp? {
        val psi = code.exprAt(0) ?: return null
        val call = psi.parentsWithSelf.filterIsInstance<KtCallExpression>().firstOrNull() ?: return null
        val candidates = candidates(call)
        val activeDeclaration = activeDeclaration(call, candidates)
        val activeParameter = activeParameter(call)

        return KotlinSignatureHelp(candidates, activeDeclaration, activeParameter)
    }

    private fun candidates(call: KtCallExpression): List<CallableDescriptor> {
        val target = call.calleeExpression!!
        val identifier = target.text
        val dotParent = target.findParent<KtDotQualifiedExpression>()
        if (dotParent != null) {
            val type = code.getType(dotParent.receiverExpression) ?: return emptyList()

            return memberOverloads(type, identifier).toList()
        }
        val idParent = target.findParent<KtNameReferenceExpression>()
        if (idParent != null) {
            val scope = code.findScope(idParent) ?: return emptyList()

            return identifierOverloads(scope, identifier).toList()
        }
        return emptyList()
    }

    private fun activeDeclaration(call: KtCallExpression, candidates: List<CallableDescriptor>): Int {
        return candidates.indexOfFirst { isCompatibleWith(call, it) }
    }

    private fun isCompatibleWith(call: KtCallExpression, candidate: CallableDescriptor): Boolean {
        val argumentList = call.valueArgumentList ?: return true
        val nArguments = argumentList.text.count { it == ',' } + 1
        if (nArguments > candidate.valueParameters.size)
            return false

        for (arg in call.valueArguments) {
            if (arg.isNamed()) {
                if (candidate.valueParameters.none { arg.name == it.name.identifier })
                    return false
            }
            // TODO consider types as well
        }

        return true
    }

    private fun activeParameter(call: KtCallExpression): Int {
        val cursor = code.cursor()
        val args = call.valueArgumentList ?: return -1
        val text = args.text
        val beforeCursor = text.subSequence(0, cursor - args.textRange.startOffset)

        return beforeCursor.count { it == ','}
    }
}

data class KotlinSignatureHelp(val declarations: List<CallableDescriptor>, val activeDeclaration: Int, val activeParameter: Int)