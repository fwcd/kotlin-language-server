package org.javacs.kt.symbols

import com.intellij.psi.PsiElement
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.javacs.kt.SourcePath
import org.javacs.kt.diagnostic.toPath
import org.javacs.kt.docs.preOrderTraversal
import org.javacs.kt.position.range
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents

fun documentSymbols(file: KtFile): Sequence<KtNamedDeclaration> =
        file.preOrderTraversal().mapNotNull { pickImportantElements(it, true) }

fun workspaceSymbols(sources: SourcePath): Sequence<KtNamedDeclaration> =
        sources.allSources().values.asSequence().flatMap(::doWorkspaceSymbols)

private fun doWorkspaceSymbols(file: KtFile): Sequence<KtNamedDeclaration> =
    file.preOrderTraversal().mapNotNull { pickImportantElements(it, false) }

private fun pickImportantElements(node: PsiElement, includeLocals: Boolean): KtNamedDeclaration? =
        when (node) {
            is KtClassOrObject -> if (node.name == null) null else node
            is KtTypeAlias -> node
            is KtConstructor<*> -> node
            is KtNamedFunction -> if (!node.isLocal || includeLocals) node else null
            is KtProperty -> if (!node.isLocal || includeLocals) node else null
            is KtVariableDeclaration -> if (includeLocals) node else null
            else -> null
        }

fun symbolInformation(d: KtNamedDeclaration): SymbolInformation? {
    val name = d.name ?: return null

    return SymbolInformation(name, symbolKind(d), symbolLocation(d), symbolContainer(d))
}

private fun symbolKind(d: KtNamedDeclaration): SymbolKind =
        when (d) {
            is KtClassOrObject -> SymbolKind.Class
            is KtTypeAlias -> SymbolKind.Interface
            is KtConstructor<*> -> SymbolKind.Constructor
            is KtNamedFunction -> SymbolKind.Function
            is KtProperty -> SymbolKind.Property
            is KtVariableDeclaration -> SymbolKind.Variable
            else -> throw IllegalArgumentException("Unexpected symbol $d")
        }

private fun symbolLocation(declaration: KtNamedDeclaration): Location {
    val file = declaration.containingFile
    val uri = file.toPath().toUri().toString()
    val range = range(file.text, declaration.textRange)

    return Location(uri, range)
}

private fun symbolContainer(declaration: KtNamedDeclaration): String? =
        declaration.parents
                .filterIsInstance<KtNamedDeclaration>()
                .firstOrNull()
                ?.fqName.toString()