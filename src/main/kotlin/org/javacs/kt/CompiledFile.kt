package org.javacs.kt

import com.intellij.openapi.util.TextRange
import org.javacs.kt.RecompileStrategy.*
import org.javacs.kt.RecompileStrategy.Function
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import java.nio.file.Path
import kotlin.math.max

enum class RecompileStrategy {
    Function,
    File,
    NoChanges,
    Impossible
}

class CompiledFile(private val path: Path, val file: KtFile, private val context: BindingContext) {

    fun recompile(newText: String, cursor: Int): RecompileStrategy {
        // If there are no changes, we can use the existing analyze
        val changed = changedRegion(newText) ?: return NoChanges
        // Look for a recoverable expression around the cursor
        val leaf = file.findElementAt(cursor) ?: return Impossible
        val surroundingFunction = leaf.parentsWithSelf.filterIsInstance<KtNamedFunction>().firstOrNull() ?: return File
        // If the expression that we're going to re-compile doesn't include all the changes, give up
        if (!surroundingFunction.bodyExpression!!.textRange.contains(changed)) return File
        // If the function body doesn't have scope, give up
        val scope = context.get(BindingContext.LEXICAL_SCOPE, surroundingFunction.bodyExpression) ?: return File

        return Function
    }

    fun compiledCode(cursor: Int, sourcePath: Collection<KtFile>): CompiledCode {
        return CompiledCode(file.text, file, context, cursor, 0, sourcePath)
    }

    /**
     * Re-analyze a single function declaration
     */
    fun recompileFunction(newText: String, cursor: Int, sourcePath: Collection<KtFile>): CompiledCode {
        val surroundingFunction = file.findElementAt(cursor)!!.parentsWithSelf.filterIsInstance<KtNamedFunction>().firstOrNull()!!
        val scope = context.get(BindingContext.LEXICAL_SCOPE, surroundingFunction.bodyExpression)!!
        val start = surroundingFunction.textRange.startOffset
        val end = surroundingFunction.textRange.endOffset + newText.length - file.text.length
        val newFunctionText = newText.substring(start, end)
        val newFunction = Compiler.parser.createFunction(newFunctionText)
        val newContext = Compiler.compileExpression(newFunction, scope, sourcePath)

        return CompiledCode(newText, newFunction, newContext, cursor, surroundingFunction.textRange.startOffset, sourcePath)
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
}