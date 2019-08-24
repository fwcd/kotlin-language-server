package org.javacs.kt

import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.javacs.kt.position.changedRegion
import org.javacs.kt.position.position
import org.javacs.kt.util.findParent
import org.javacs.kt.util.nullResult
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.KotlinType

class CompiledFile(
        val content: String,
        val parse: KtFile,
        val compile: BindingContext,
        val container: ComponentProvider,
        val sourcePath: Collection<KtFile>,
        val classPath: CompilerClassPath) {

    /**
     * Find the type of the expression at `cursor`
     */
    fun typeAtPoint(cursor: Int): KotlinType? {
        var cursorExpr = parseAtPoint(cursor)?.findParent<KtExpression>() ?: return nullResult("Couldn't find expression at ${describePosition(cursor)}")
        val surroundingExpr = expandForType(cursor, cursorExpr)
        val scope = scopeAtPoint(cursor) ?: return nullResult("Couldn't find scope at ${describePosition(cursor)}")
        return typeOfExpression(surroundingExpr, scope)
    }

    fun typeOfExpression(expression: KtExpression, scopeWithImports: LexicalScope): KotlinType? =
            bindingContextOf(expression, scopeWithImports).getType(expression)

    fun bindingContextOf(expression: KtExpression, scopeWithImports: LexicalScope): BindingContext =
            classPath.compiler.compileExpression(expression, scopeWithImports, sourcePath).first

    private fun expandForType(cursor: Int, surroundingExpr: KtExpression): KtExpression {
        val dotParent = surroundingExpr.parent as? KtDotQualifiedExpression
        if (dotParent != null && dotParent.selectorExpression?.textRange?.contains(cursor) ?: false) {
            return expandForType(cursor, dotParent)
        }
        else return surroundingExpr
    }

    fun referenceAtPoint(cursor: Int): Pair<KtExpression, DeclarationDescriptor>? {
        val element = parseAtPoint(cursor)
        var cursorExpr = element?.findParent<KtExpression>() ?: return nullResult("Couldn't find expression at ${describePosition(cursor)} (only found $element)")
        val surroundingExpr = expandForReference(cursor, cursorExpr)
        val scope = scopeAtPoint(cursor) ?: return nullResult("Couldn't find scope at ${describePosition(cursor)}")
        val context = bindingContextOf(surroundingExpr, scope)
        LOG.info("Hovering {}", surroundingExpr)
        return referenceFromContext(cursor, context)
    }

    private fun referenceFromContext(cursor: Int, context: BindingContext): Pair<KtExpression, DeclarationDescriptor>? {
        val targets = context.getSliceContents(BindingContext.REFERENCE_TARGET)
        return targets.asSequence()
                .filter { cursor in it.key.textRange }
                .sortedBy { it.key.textRange.length }
                .map { it.toPair() }
                .firstOrNull()
    }

    private fun expandForReference(cursor: Int, surroundingExpr: KtExpression): KtExpression {
        val parent: KtExpression? =
            (surroundingExpr.parent as? KtDotQualifiedExpression)?.let { it as KtExpression } // foo.bar
            ?: (surroundingExpr.parent as? KtSafeQualifiedExpression)?.let { it as KtExpression } // foo?.bar
            ?: (surroundingExpr.parent as? KtCallExpression)?.let { it as KtExpression } // foo()
        return parent?.let { expandForReference(cursor, it) } ?: surroundingExpr
    }

    /**
     * Parse the expression at `cursor`
     */
    fun parseAtPoint(cursor: Int): KtElement? {
        val oldCursor = oldOffset(cursor)
        val oldChanged = changedRegion(parse.text, content)?.first ?: TextRange(cursor, cursor)
        val psi = parse.findElementAt(oldCursor) ?: return nullResult("Couldn't find anything at ${describePosition(cursor)}")
        val oldParent = psi.parentsWithSelf
                .filterIsInstance<KtDeclaration>()
                .firstOrNull { it.textRange.contains(oldChanged) } ?: parse
        val recoveryRange = oldParent.textRange

        LOG.info("Re-parsing {}", describeRange(recoveryRange, true))
        var surroundingContent = content.substring(recoveryRange.startOffset, content.length - (parse.text.length - recoveryRange.endOffset))
        var offset = recoveryRange.startOffset

        if (!((oldParent as? KtParameter)?.hasValOrVar() ?: true)) {
            // Prepend 'val' to (e.g. function) parameters
            val prefix = "val "
            surroundingContent = prefix + surroundingContent
            offset -= prefix.length
        }

        val padOffset = " ".repeat(offset)
        val recompile = classPath.compiler.createFile(padOffset + surroundingContent)
        return recompile.findElementAt(cursor)?.findParent<KtElement>()
    }

    /**
     * Get the typed, compiled element at `cursor`.
     * This may be out-of-date if the user is typing quickly.
     */
    fun elementAtPoint(cursor: Int): KtElement? {
        val oldCursor = oldOffset(cursor)
        val psi = parse.findElementAt(oldCursor) ?: return nullResult("Couldn't find anything at ${describePosition(cursor)}")
        return psi.findParent<KtElement>()
    }

    /**
     * Find the lexical-scope surrounding `cursor`.
     * This may be out-of-date if the user is typing quickly.
     */
    fun scopeAtPoint(cursor: Int): LexicalScope? {
        val oldCursor = oldOffset(cursor)
        return compile.getSliceContents(BindingContext.LEXICAL_SCOPE).asSequence()
                .filter { it.key.textRange.startOffset <= oldCursor && oldCursor <= it.key.textRange.endOffset }
                .sortedBy { it.key.textRange.length  }
                .map { it.value }
                .firstOrNull()
    }

    fun lineBefore(cursor: Int): String = content.substring(0, cursor).substringAfterLast('\n')

    fun lineAfter(cursor: Int): String = content.substring(cursor).substringBefore('\n')

    private fun oldOffset(cursor: Int): Int {
        val (oldChanged, newChanged) = changedRegion(parse.text, content) ?: return cursor

        return when {
            cursor <= newChanged.startOffset -> cursor
            cursor < newChanged.endOffset -> {
                val newRelative = cursor - newChanged.startOffset
                val oldRelative = newRelative * oldChanged.length / newChanged.length
                oldChanged.startOffset + oldRelative
            }
            else -> parse.text.length - (content.length - cursor)
        }
    }

    fun describePosition(offset: Int, oldContent: Boolean = false): String {
        val c = if (oldContent) parse.text else content
        val pos = position(c, offset)
        val file = parse.toPath().fileName

        return "$file ${pos.line + 1}:${pos.character + 1}"
    }

    private fun describeRange(range: TextRange, oldContent: Boolean = false): String {
        val c = if (oldContent) parse.text else content
        val start = position(c, range.startOffset)
        val end = position(c, range.endOffset)
        val file = parse.toPath().fileName

        return "$file ${start.line}:${start.character + 1}-${end.line + 1}:${end.character + 1}"
    }
}

private fun fileName(file: KtFile): String {
    val parts = file.name.split('/')

    return parts.last()
}
