package org.javacs.kt.codelens

import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Range
import org.javacs.kt.CompiledFile
import org.javacs.kt.LOG
import org.javacs.kt.position.location
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import java.nio.file.Paths

fun findCodeLenses(file: CompiledFile): List<CodeLens> {
    val codeLenses = mutableListOf<CodeLens>()
    val parsedFile = file.parse
    val filePath = Paths.get(parsedFile.containingFile.virtualFile.path)
    val uri = filePath.toUri().toString()

    // Add code lenses for classes and interfaces
    parsedFile.declarations.filterIsInstance<KtClassOrObject>().forEach { ktClass ->
        val classDesc = file.compile.get(BindingContext.CLASS, ktClass)
        if (classDesc != null) {
            when (classDesc.kind) {
                ClassKind.INTERFACE -> {
                    // Add "Show Implementations" code lens for interfaces
                    location(ktClass)?.let { loc ->
                        codeLenses.add(CodeLens(
                            loc.range,
                            Command("Show Implementations", "kotlin.showImplementations", listOf(uri, loc.range.start.line, loc.range.start.character)),
                            null
                        ))
                    }
                }
                ClassKind.CLASS -> {
                    // Add "Show Subclasses" code lens for classes
                    location(ktClass)?.let { loc ->
                        codeLenses.add(CodeLens(
                            loc.range,
                            Command("Show Subclasses", "kotlin.showSubclasses", listOf(uri, loc.range.start.line, loc.range.start.character)),
                            null
                        ))
                    }
                }
                else -> {}
            }
        }
    }

    // Add code lenses for functions
    parsedFile.declarations.filterIsInstance<KtNamedFunction>().forEach { ktFunction ->
        location(ktFunction)?.let { loc ->
            codeLenses.add(CodeLens(
                loc.range,
                Command("Show References", "kotlin.showReferences", listOf(uri, loc.range.start.line, loc.range.start.character)),
                null
            ))
        }
    }

    return codeLenses
} 