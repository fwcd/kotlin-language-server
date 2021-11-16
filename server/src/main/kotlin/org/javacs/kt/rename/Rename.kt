package org.javacs.kt.rename

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledFile
import org.javacs.kt.SourcePath
import org.javacs.kt.position.location
import org.javacs.kt.references.findReferences
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtNamedDeclaration

fun renameSymbol(file: CompiledFile, cursor: Int, sp: SourcePath, newName: String): WorkspaceEdit? {
    val (declaration, location) = findDeclaration(file, cursor) ?: return null
    return declaration.let {
        val declarationEdit = Either.forLeft<TextDocumentEdit, ResourceOperation>(TextDocumentEdit(
            VersionedTextDocumentIdentifier().apply { uri = location.uri },
            listOf(TextEdit(location.range, newName))
        ))

        val referenceEdits = findReferences(declaration, sp).map {
            Either.forLeft<TextDocumentEdit, ResourceOperation>(TextDocumentEdit(
                VersionedTextDocumentIdentifier().apply { uri = it.uri },
                listOf(TextEdit(it.range, newName))
            ))
        }

        WorkspaceEdit(listOf(declarationEdit) + referenceEdits)
    }
}

private fun findDeclaration(file: CompiledFile, cursor: Int): Pair<KtNamedDeclaration, Location>? {
    val (_, target) = file.referenceAtPoint(cursor) ?: return null
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
