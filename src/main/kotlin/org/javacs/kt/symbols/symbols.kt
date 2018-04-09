package org.javacs.kt.symbols

import com.intellij.psi.PsiElement
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.javacs.kt.CompiledFile
import org.javacs.kt.SourcePath
import org.javacs.kt.diagnostic.toPath
import org.javacs.kt.docs.preOrderTraversal
import org.javacs.kt.position.range
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext

fun documentSymbols(file: CompiledFile): Sequence<DeclarationDescriptor> =
        file.file.preOrderTraversal().mapNotNull { pickDeclarations(file, it) }

fun pickDeclarations(file: CompiledFile, node: PsiElement): DeclarationDescriptor? =
        when (node) {
            is KtNamedDeclaration -> file.context.get(BindingContext.DECLARATION_TO_DESCRIPTOR, node)
            else -> null
        }

fun workspaceSymbols(sources: SourcePath): Sequence<KtNamedDeclaration> {
    val open = sources.openFiles.asSequence().flatMap { doWorkspaceSymbols(it.value.compiled.file) }
    val disk = sources.diskFiles.values.asSequence().flatMap { doWorkspaceSymbols(it) }

    return open + disk
}

// TODO this logic is more general and should be combined with documentSymbol implementation
private fun doWorkspaceSymbols(file: KtFile): Sequence<KtNamedDeclaration> {
    return file.preOrderTraversal().mapNotNull(::pickImportantElements)
}

private fun pickImportantElements(node: PsiElement): KtNamedDeclaration? =
        when (node) {
            is KtClassOrObject -> if (node.name == null) null else node
            is KtTypeAlias -> node
            is KtConstructor<*> -> node
            is KtNamedFunction -> if (node.isLocal) null else node
            is KtProperty -> if (node.isLocal) null else node
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