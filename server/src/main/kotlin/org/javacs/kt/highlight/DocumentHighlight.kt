package org.javacs.kt.highlight

import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.Location
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.range
import org.javacs.kt.references.findReferencesToDeclarationInFile
import org.javacs.kt.rename.findDeclaration
import org.javacs.kt.util.findParent
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration

fun documentHighlightsAt(file: CompiledFile, cursor: Int): List<DocumentHighlight> {
    val (declaration, declarationLocation) = findDeclaration(file, cursor)
        ?: findDeclarationCursorSite(file, cursor)
        ?: return emptyList()
    val references = findReferencesToDeclarationInFile(declaration, file)

    return if (declaration.isInFile(file.parse)) {
        listOf(DocumentHighlight(declarationLocation.range, DocumentHighlightKind.Text))
    } else {
        emptyList()
    } + references.map { DocumentHighlight(it, DocumentHighlightKind.Text) }
}

private fun findDeclarationCursorSite(
        file: CompiledFile,
        cursor: Int
): Pair<KtNamedDeclaration, Location>? {
    // current symbol might be a declaration. This function is used as a fallback when
    // findDeclaration fails
    val declaration = file.elementAtPoint(cursor)?.findParent<KtNamedDeclaration>()

    return declaration?.let {
        // in this scenario we know that the declaration will be at the cursor site, so uri is not
        // important
        Pair(it,
             Location("",
                      range(file.content, it.nameIdentifier?.textRangeInParent ?: return null)))
    }
}

private fun KtNamedDeclaration.isInFile(file: KtFile) = this.containingFile == file
