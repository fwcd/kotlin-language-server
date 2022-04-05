package org.javacs.kt.imports

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.javacs.kt.position.location

fun getImportTextEditEntry(parsedFile: KtFile, fqName: FqName): TextEdit {
    val imports = parsedFile.importDirectives
    val importedNames = imports
        .mapNotNull { it.importedFqName?.shortName() }
        .toSet()
    
    val pos = findImportInsertionPosition(parsedFile, fqName)
    val prefix = if (importedNames.isEmpty()) "\n\n" else "\n"
    return TextEdit(Range(pos, pos), "${prefix}import ${fqName}")
}

/** Finds a good insertion position for a new import of the given fully-qualified name. */
private fun findImportInsertionPosition(parsedFile: KtFile, fqName: FqName): Position =
    (closestImport(parsedFile.importDirectives, fqName) as? KtElement ?: parsedFile.packageDirective as? KtElement)
        ?.let(::location)
        ?.range
        ?.end
        ?: Position(0, 0)

// TODO: Lexicographic insertion
private fun closestImport(imports: List<KtImportDirective>, fqName: FqName): KtImportDirective? =
    imports
        .asReversed()
        .maxByOrNull { it.importedFqName?.let { matchingPrefixLength(it, fqName) } ?: 0 }

private fun matchingPrefixLength(left: FqName, right: FqName): Int =
    left.pathSegments().asSequence().zip(right.pathSegments().asSequence())
        .takeWhile { it.first == it.second }
        .count()
