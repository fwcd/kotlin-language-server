package org.javacs.kt

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.javacs.kt.completion.*
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.KotlinType

data class KotlinSignatureHelp(val declarations: List<CallableDescriptor>, val activeDeclaration: Int, val activeParameter: Int)

/**
 * @param surrounding An element that surrounds the cursor
 * @param context Result of type-checking `surrounding`
 * @param cursor The user's cursor
 * @param textOffset Offset between the coordinate system of `surrounding` and the coordinates of `cursor`
 */
class CompiledCode(
        val fileContent: String,
        private val surrounding: KtElement,
        private val context: BindingContext,
        private val cursor: Int,
        private val textOffset: Int,
        private val sourcePath: Collection<KtFile>) {

    fun signatureHelp(): KotlinSignatureHelp? {
        val psi = surrounding.findElementAt(cursor - textOffset) ?: return null
        val call = psi.parentsWithSelf.filterIsInstance<KtCallExpression>().firstOrNull() ?: return null
        val candidates = candidates(call)
        val activeDeclaration = activeDeclaration(call, candidates)
        val activeParameter = activeParameter(call, cursor)

        return KotlinSignatureHelp(candidates, activeDeclaration, activeParameter)
    }

    private fun candidates(call: KtCallExpression): List<CallableDescriptor> {
        val target = call.calleeExpression!!
        val identifier = target.text
        val dotParent = find<KtDotQualifiedExpression>(target)
        if (dotParent != null) {
            val type = context.getType(dotParent.receiverExpression)
                       ?: robustType(dotParent.receiverExpression, context)
                       ?: return emptyList()

            return memberOverloads(type, identifier).toList()
        }
        val idParent = find<KtNameReferenceExpression>(target)
        if (idParent != null) {
            val scope = findScope(idParent, context) ?: return emptyList()

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

    private fun activeParameter(call: KtCallExpression, cursor: Int): Int {
        val args = call.valueArgumentList ?: return -1
        val text = args.text
        val beforeCursor = text.subSequence(0, cursor - args.textRange.startOffset)

        return beforeCursor.count { it == ','}
    }

    fun hover(): Pair<TextRange, DeclarationDescriptor>? {
        val psi = surrounding.findElementAt(cursor - textOffset) ?: return null
        val stopAtDeclaration = psi.parentsWithSelf.takeWhile { it !is KtDeclaration }
        val onlyExpressions = stopAtDeclaration.filterIsInstance<KtExpression>()
        val hasHover = onlyExpressions.filter { doHover(it) != null }
        val expr = hasHover.firstOrNull() ?: return null
        val range = expr.textRange.shiftRight(textOffset)
        val hover = doHover(expr)!!

        return Pair(range, hover)
    }

    private fun doHover(expr: KtExpression): DeclarationDescriptor? {
        return when (expr) {
            is KtReferenceExpression -> context.get(BindingContext.REFERENCE_TARGET, expr) ?: return null
            else -> null
        }
    }

    fun completions(): Sequence<DeclarationDescriptor> {
        val psi = surrounding.findElementAt(cursor - textOffset - 1) ?: return emptySequence()
        val expr = find<KtExpression>(psi) ?: return emptySequence()
        val typeParent = find<KtTypeElement>(expr)
        if (typeParent != null) {
            val scope = findScope(expr, context) ?: return emptySequence()
            val partial = matchIdentifier(expr)

            return completeTypes(scope, partial)
        }
        val dotParent = find<KtDotQualifiedExpression>(expr)
        if (dotParent != null) {
            val type = context.getType(dotParent.receiverExpression)
                       ?: robustType(dotParent.receiverExpression, context)
                       ?: return emptySequence()
            val partial = matchIdentifier(dotParent.selectorExpression)

            return completeMembers(type, partial)
        }
        val idParent = find<KtNameReferenceExpression>(expr)
        if (idParent != null) {
            val scope = findScope(idParent, context) ?: return emptySequence()
            val partial = matchIdentifier(expr)

            return completeIdentifiers(scope, partial)
        }

        return emptySequence()
    }

    private fun matchIdentifier(exprAtCursor: KtExpression?): String {
        val select = exprAtCursor?.text ?: ""
        val word = Regex("[^()]+")

        return word.find(select)?.value ?: ""
    }

    /**
     * If we're having trouble figuring out the type of an expression,
     * try re-parsing and re-analyzing just the difficult expression
     */
    fun robustType(expr: KtExpression, context: BindingContext): KotlinType? {
        val scope = findScope(expr, context) ?: return null
        val parse = Compiler.createExpression(expr.text)
        val analyze = Compiler.compileExpression(parse, scope, sourcePath)

        return analyze.getType(parse)
    }

    private fun findScope(expr: KtExpression, context: BindingContext): LexicalScope? {
        return expr.parentsWithSelf.filterIsInstance<KtElement>().mapNotNull {
            context.get(BindingContext.LEXICAL_SCOPE, it)
        }.firstOrNull()
    }
}

private inline fun<reified Find> find(cursor: PsiElement) =
        cursor.parentsWithSelf.filterIsInstance<Find>().firstOrNull()