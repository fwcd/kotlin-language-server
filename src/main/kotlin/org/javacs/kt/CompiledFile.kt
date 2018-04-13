package org.javacs.kt

import org.javacs.kt.RecompileStrategy.*
import org.javacs.kt.RecompileStrategy.Function
import org.javacs.kt.position.changedRegion
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext

enum class RecompileStrategy {
    Function,
    File,
    NoChanges,
    Impossible
}

class CompiledFile(
        private val content: String,
        private val compiledFile: KtFile,
        private val compiledContext: BindingContext,
        private val sourcePath: Collection<KtFile>,
        private val cp: CompilerClassPath) {
    fun recompile(cursor: Int): RecompileStrategy {
        // If there are no changes, we can use the existing analyze
        val (oldChanged, _) = changedRegion(compiledFile.text, content) ?: return run {
            LOG.info("${compiledFile.name} has not changed")
            NoChanges
        }
        // Look for a recoverable expression around the cursor
        val oldCursor = oldCursor(cursor)
        val leaf = compiledFile.findElementAt(oldCursor) ?: run {
            return if (oldChanged.contains(oldCursor)) {
                LOG.info("No element at ${compiledFile.name}:$cursor, inside changed region")
                File
            } else {
                LOG.info("No element at ${compiledFile.name}:$cursor")
                Impossible
            }
        }
        val surroundingFunction = leaf.parentsWithSelf.filterIsInstance<KtNamedFunction>().firstOrNull() ?: run {
            LOG.info("No surrounding function at ${compiledFile.name}:$cursor")
            return File
        }
        // If the expression that we're going to re-compile doesn't include all the changes, give up
        val willRepair = surroundingFunction.bodyExpression!!.textRange
        if (!willRepair.contains(oldChanged)) {
            LOG.info("Changed region ${compiledFile.name}:$oldChanged is outside ${surroundingFunction.name} $willRepair")
            return File
        }
        // If the function body doesn't have scope, give up
        val scope = compiledContext.get(BindingContext.LEXICAL_SCOPE, surroundingFunction.bodyExpression) ?: run {
            LOG.info("${surroundingFunction.name} has no scope")
            return File
        }

        LOG.info("Successfully recovered at ${compiledFile.name}:$cursor using ${surroundingFunction.name}")
        return Function
    }

    private fun oldCursor(cursor: Int): Int {
        val (oldChanged, newChanged) = changedRegion(compiledFile.text, content) ?: return cursor

        return when {
            cursor <= newChanged.startOffset -> cursor
            cursor < newChanged.endOffset -> {
                val newRelative = cursor - newChanged.startOffset
                val oldRelative = newRelative * oldChanged.length / newChanged.length
                oldChanged.startOffset + oldRelative
            }
            else -> compiledFile.text.length - (content.length - cursor)
        }
    }

    fun compiledCode(cursor: Int): CompiledCode {
        return CompiledCode(compiledFile.text, compiledFile, compiledContext, cursor, 0, cp.compiler, sourcePath)
    }

    /**
     * Re-analyze a single function declaration
     */
    fun recompileFunction(cursor: Int): CompiledCode {
        val oldCursor = oldCursor(cursor)
        val surroundingFunction = compiledFile.findElementAt(oldCursor)!!.parentsWithSelf.filterIsInstance<KtNamedFunction>().firstOrNull()!!
        val scope = compiledContext.get(BindingContext.LEXICAL_SCOPE, surroundingFunction.bodyExpression)!!
        val start = surroundingFunction.textRange.startOffset
        val end = surroundingFunction.textRange.endOffset + content.length - compiledFile.text.length
        val newFunctionText = content.substring(start, end)
        val newFunction = cp.compiler.createFunction(newFunctionText)
        val newContext = cp.compiler.compileExpression(newFunction, scope, sourcePath)

        return CompiledCode(
                content,
                newFunction,
                newContext,
                cursor,
                surroundingFunction.textRange.startOffset,
                cp.compiler,
                sourcePath)
    }
}
