package org.javacs.kt

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.javacs.kt.compiler.CompilationKind
import org.javacs.kt.position.changedRegion
import org.javacs.kt.position.location
import org.javacs.kt.position.position
import org.javacs.kt.position.range
import org.javacs.kt.position.toURIString
import org.javacs.kt.util.findParent
import org.javacs.kt.util.nullResult
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.KotlinType
import org.eclipse.lsp4j.Location
import java.nio.file.Path
import java.nio.file.Paths

class CompiledFile(
    val content: String,
    val parse: KtFile,
    val compile: BindingContext,
    val module: ModuleDescriptor,
    val sourcePath: Collection<KtFile>,
    val classPath: CompilerClassPath,
    val isScript: Boolean = false,
    val kind: CompilationKind = CompilationKind.DEFAULT
) {
    /**
     * Find the type of the expression at `cursor`
     */
    fun typeAtPoint(cursor: Int): KotlinType? {
        val cursorExpr = parseAtPoint(cursor, asReference = true)?.findParent<KtExpression>()
            ?: return nullResult("Couldn't find expression at ${describePosition(cursor)}")
        val surroundingExpr = expandForType(cursor, cursorExpr)
        val scope = scopeAtPoint(cursor) ?: return nullResult("Couldn't find scope at ${describePosition(cursor)}")
        return typeOfExpression(surroundingExpr, scope)
    }

    fun typeOfExpression(expression: KtExpression, scopeWithImports: LexicalScope): KotlinType? =
        bindingContextOf(expression, scopeWithImports)?.getType(expression)

    fun bindingContextOf(expression: KtExpression, scopeWithImports: LexicalScope): BindingContext? =
        classPath.compiler.compileKtExpression(expression, scopeWithImports, sourcePath, kind)?.first

    private fun expandForType(cursor: Int, surroundingExpr: KtExpression): KtExpression {
        val dotParent = surroundingExpr.parent as? KtDotQualifiedExpression
        return if (dotParent != null && dotParent.selectorExpression?.textRange?.contains(cursor) == true) {
            expandForType(cursor, dotParent)
        } else surroundingExpr
    }

    /**
     * Looks for a reference expression at the given cursor.
     * This is currently used by many features in the language server.
     * Unfortunately, it fails to find declarations for JDK symbols.
     * [referenceExpressionAtPoint] provides an alternative implementation that can find JDK symbols.
     * It cannot, however, replace this method at the moment.
     * TODO: Investigate why this method doesn't find JDK symbols.
     */
    fun referenceAtPoint(cursor: Int): Pair<KtExpression, DeclarationDescriptor>? {
        val element = parseAtPoint(cursor, asReference = true)
        val cursorExpr = element?.findParent<KtExpression>() ?: return nullResult(
            "Couldn't find expression at ${
                describePosition(cursor)
            } (only found $element)"
        )
        val surroundingExpr = expandForReference(cursor, cursorExpr)
        val scope = scopeAtPoint(cursor) ?: return nullResult("Couldn't find scope at ${describePosition(cursor)}")
        // NOTE: Due to our tiny-fake-file mechanism, we may have `path == /dummy.virtual.kt != parse.containingFile.toPath`
        val path = surroundingExpr.containingFile.toPath()
        val context = bindingContextOf(surroundingExpr, scope)
        LOG.info("Hovering {}", surroundingExpr)
        return context?.let { referenceFromContext(cursor, path, it) }
    }

    /**
     * Looks for a reference expression at the given cursor.
     * This method is similar to [referenceAtPoint], but the latter fails to find declarations for JDK symbols.
     * This method should not be used for anything other than finding definitions (at least for now).
     */
    fun referenceExpressionAtPoint(cursor: Int): Pair<KtExpression, DeclarationDescriptor>? {
        val path = parse.containingFile.toPath()
        return referenceFromContext(cursor, path, compile)
    }

    private fun referenceFromContext(
        cursor: Int,
        path: Path,
        context: BindingContext
    ): Pair<KtExpression, DeclarationDescriptor>? {
        val targets = context.getSliceContents(BindingContext.REFERENCE_TARGET)
        return targets.asSequence()
            .filter { cursor in it.key.textRange && it.key.containingFile.toPath() == path }
            .sortedBy { it.key.textRange.length }
            .map { it.toPair() }
            .firstOrNull()
    }

    private fun expandForReference(cursor: Int, surroundingExpr: KtExpression): KtExpression {
        val parent: KtExpression? =
            surroundingExpr.parent as? KtDotQualifiedExpression // foo.bar
                ?: surroundingExpr.parent as? KtSafeQualifiedExpression // foo?.bar
                ?: surroundingExpr.parent as? KtCallExpression // foo()
        return parent?.let { expandForReference(cursor, it) } ?: surroundingExpr
    }

    /**
     * Parse the expression at `cursor`.
     *
     * If the `asReference` flag is set, the method will attempt to
     * convert a declaration (e.g. of a class or a function) to a referencing
     * expression before parsing it.
     */
    fun parseAtPoint(cursor: Int, asReference: Boolean = false): KtElement? {
        val oldCursor = oldOffset(cursor)
        val oldChanged = changedRegion(parse.text, content)?.first ?: TextRange(cursor, cursor)
        val psi =
            parse.findElementAt(oldCursor) ?: return nullResult("Couldn't find anything at ${describePosition(cursor)}")
        val oldParent = psi.parentsWithSelf
            .filterIsInstance<KtDeclaration>()
            .firstOrNull { it.textRange.contains(oldChanged) } ?: parse

        LOG.debug { "PSI path: ${psi.parentsWithSelf.toList()}" }

        val (surroundingContent, offset) = contentAndOffsetFromElement(psi, oldParent, asReference)
        val padOffset = " ".repeat(offset)
        val recompile = classPath.compiler.createKtFile(
            padOffset + surroundingContent,
            Paths.get("dummy.virtual" + if (isScript) ".kts" else ".kt"),
            kind
        )
        return recompile.findElementAt(cursor)?.findParent<KtElement>()
    }

    /**
     * Extracts the surrounding content and the text offset from a
     * PSI element.
     *
     * See `parseAtPoint` for documentation of the `asReference` flag.
     */
    private fun contentAndOffsetFromElement(
        psi: PsiElement,
        parent: KtElement,
        asReference: Boolean
    ): Pair<String, Int> {
        var surroundingContent: String
        var offset: Int

        if (asReference) {
            // Convert the declaration into a fake reference expression
            when {
                parent is KtClass && psi.node.elementType == KtTokens.IDENTIFIER -> {
                    // Converting class name identifier: Use a fake property with the class name as type
                    //                                   Otherwise the compiler/analyzer would throw an exception due to a missing TopLevelDescriptorProvider
                    val prefix = "val x: "
                    surroundingContent = prefix + psi.text
                    offset = psi.textRange.startOffset - prefix.length

                    return Pair(surroundingContent, offset)
                }
            }
        }

        // Otherwise just use the expression
        val recoveryRange = parent.textRange
        LOG.info("Re-parsing {}", describeRange(recoveryRange, true))

        surroundingContent =
            content.substring(recoveryRange.startOffset, content.length - (parse.text.length - recoveryRange.endOffset))
        offset = recoveryRange.startOffset

        if (asReference && (parent as? KtParameter)?.hasValOrVar() == false) {
            // Prepend 'val' to (e.g. function) parameters
            val prefix = "val "
            surroundingContent = prefix + surroundingContent
            offset -= prefix.length
        }

        return Pair(surroundingContent, offset)
    }

    /**
     * Get the typed, compiled element at `cursor`.
     * This may be out-of-date if the user is typing quickly.
     */
    fun elementAtPoint(cursor: Int): KtElement? {
        val oldCursor = oldOffset(cursor)
        val psi =
            parse.findElementAt(oldCursor) ?: return nullResult("Couldn't find anything at ${describePosition(cursor)}")
        return psi.findParent<KtElement>()
    }


    /**
     * Find the declaration of the element at the cursor.
     */
    fun findDeclaration(cursor: Int): Pair<KtNamedDeclaration, Location>? =
        findDeclarationReference(cursor) ?: findDeclarationCursorSite(cursor)

    /**
     * Find the declaration of the element at the cursor. Only works if the element at the cursor is a reference.
     */
    private fun findDeclarationReference(cursor: Int): Pair<KtNamedDeclaration, Location>? {
        val (_, target) = referenceAtPoint(cursor) ?: return null
        val psi = target.findPsi()

        return if (psi is KtNamedDeclaration) {
            psi.nameIdentifier?.let {
                location(it)?.let { location ->
                    Pair(psi, location)
                }
            }
        } else {
            null
        }
    }

    /**
     * Find the declaration of the element at the cursor.
     * Works even in cases where the element at the cursor might not be a reference, so works for declaration sites.
     */
    private fun findDeclarationCursorSite(cursor: Int): Pair<KtNamedDeclaration, Location>? {
        // current symbol might be a declaration. This function is used as a fallback when
        // findDeclaration fails
        val declaration = elementAtPoint(cursor)?.findParent<KtNamedDeclaration>()

        return declaration?.let {
            Pair(
                it,
                Location(
                    it.containingFile.toURIString(),
                    range(content, it.nameIdentifier?.textRange ?: return null)
                )
            )
        }
    }

    /**
     * Find the lexical-scope surrounding `cursor`.
     * This may be out-of-date if the user is typing quickly.
     */
    fun scopeAtPoint(cursor: Int): LexicalScope? {
        val oldCursor = oldOffset(cursor)
        val path = parse.containingFile.toPath()
        return compile.getSliceContents(BindingContext.LEXICAL_SCOPE).asSequence()
            .filter {
                it.key.textRange.startOffset <= oldCursor
                    && oldCursor <= it.key.textRange.endOffset
                    && it.key.containingFile.toPath() == path
            }
            .sortedBy { it.key.textRange.length }
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
