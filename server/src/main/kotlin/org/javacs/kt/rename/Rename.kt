package org.javacs.kt.rename

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.CompiledFile
import org.javacs.kt.SourcePath
import org.javacs.kt.references.findReferences

fun renameSymbol(file: CompiledFile, cursor: Int, sp: SourcePath, newName: String): WorkspaceEdit? {
    val (declaration, location) = file.findDeclaration(cursor) ?: return null
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
