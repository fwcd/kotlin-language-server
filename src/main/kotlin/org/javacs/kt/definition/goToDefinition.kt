package org.javacs.kt.definition

import org.eclipse.lsp4j.Location
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.location

fun goToDefinition(file: CompiledFile, cursor: Int): Location? {
    val (_, target) = file.referenceAtPoint(cursor) ?: return null
    // TODO go to declaration name rather than beginning of javadoc comment
    return location(target)
}