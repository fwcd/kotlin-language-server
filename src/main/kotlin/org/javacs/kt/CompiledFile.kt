package org.javacs.kt

import com.intellij.openapi.util.TextRange
import org.javacs.kt.RecompileStrategy.*
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import java.nio.file.Path
import kotlin.math.max

enum class RecompileStrategy {
    Expression,
    File,
    NoChanges,
    Impossible
}

class CompiledFile(private val path: Path, val file: KtFile, private val context: BindingContext) {

    fun recompile(newText: String, cursor: Int): RecompileStrategy {
        // If there are no changes, we can use the existing analyze
        val changed = changedRegion(newText) ?: return NoChanges
        // Look for a recoverable expression around the cursor
        val strategy = recoverAt(newText, cursor) ?: return File
        // If the expression that we're going to re-compile doesn't include all the changes, give up
        if (!strategy.willRepair.contains(changed)) return File

        return Expression
    }

    fun compiledCode(cursor: Int, sourcePath: Collection<KtFile>): CompiledCode {
        return CompiledCode(file.text, file, context, cursor, 0, sourcePath)
    }

    /**
     * Re-analyze a single expression
     */
    fun recompileExpression(newText: String, cursor: Int, sourcePath: Collection<KtFile>): CompiledCode {
        val strategy = recoverAt(newText, cursor)!!
        val newContext = Compiler.compileExpression(strategy.newExpr, strategy.oldScope, sourcePath)

        return CompiledCode(newText, strategy.newExpr, newContext, cursor, strategy.oldRange.startOffset, sourcePath)
    }

    /**
     * Re-analyze the document if it has changed too much to be analyzed incrementally
     */
    fun recompileFile(newText: String, sourcePath: Collection<KtFile>): CompiledFile {
        LOG.info("Re-analyzing $path")

        val newFile = Compiler.createFile(path, newText)
        val newContext = Compiler.compileFile(file, sourcePath)

        return CompiledFile(path, newFile, newContext)
    }

    /**
     * Region that has been changed
     */
    private fun changedRegion(newText: String): TextRange? {
        if (file.text == newText) return null

        val prefix = file.text.commonPrefixWith(newText).length
        val suffix = file.text.commonSuffixWith(newText).length
        val end = max(file.text.length - suffix, prefix)

        LOG.info("Changed ${prefix}-${end}")

        return TextRange(prefix, end)
    }

    private fun recoverAt(newText: String, cursor: Int): RecoveryStrategy? {
        val leaf = file.findElementAt(cursor) ?: return null
        val elements = leaf.parentsWithSelf.filterIsInstance<KtElement>()
        val hasScope = elements.mapNotNull { recoveryStrategy(newText, it)}
        return hasScope.firstOrNull()
    }

    private fun recoveryStrategy(newText: String, element: KtElement): RecoveryStrategy? {
        return when (element) {
            is KtNamedFunction -> {
                val start = element.textRange.startOffset
                val end = element.textRange.endOffset + newText.length - file.text.length
                val exprText = newText.substring(start, end)
                val expr = Compiler.parser.createFunction(exprText)
                ReparseFunction(element, expr)
            }
            else -> null
        }
    }

    private interface RecoveryStrategy {
        val oldRange: TextRange
        val willRepair: TextRange
        val oldScope: LexicalScope
        val oldExpr: KtExpression
        val newExpr: KtExpression
    }

    private inner class ReparseFunction(override val oldExpr: KtNamedFunction, override val newExpr: KtNamedFunction): RecoveryStrategy {
        override val oldScope = context.get(BindingContext.LEXICAL_SCOPE, oldExpr.bodyExpression)!!
        override val oldRange = oldExpr.textRange
        override val willRepair = oldExpr.bodyExpression!!.textRange
    }
}