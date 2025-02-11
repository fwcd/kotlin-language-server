package org.javacs.kt.definition

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Range
import org.javacs.kt.CompiledFile
import org.javacs.kt.CompilerClassPath
import org.javacs.kt.ExternalSourcesConfiguration
import org.javacs.kt.LOG
import org.javacs.kt.externalsources.ClassContentProvider
import org.javacs.kt.externalsources.KlsURI
import org.javacs.kt.externalsources.toKlsURI
import org.javacs.kt.position.isZero
import org.javacs.kt.position.location
import org.javacs.kt.position.position
import org.javacs.kt.util.TemporaryDirectory
import org.javacs.kt.util.parseURI
import org.javacs.kt.util.partitionAroundLast
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/** Handles "go to definition" functionality for Kotlin code */
class DefinitionHandler(
    private val classContentProvider: ClassContentProvider,
    private val tempDir: TemporaryDirectory,
    private val config: ExternalSourcesConfiguration,
    private val compilerClassPath: CompilerClassPath,
) {
    private val cachedTempFiles = mutableMapOf<KlsURI, Path>()
    private val definitionPattern = Regex("(?:class|interface|object|fun)\\s+(\\w+)")

    /** Finds the definition location for a symbol at the given cursor position */
    fun goToDefinition(file: CompiledFile, cursor: Int): Location? {
        val (_, target) = file.referenceExpressionAtPoint(cursor) ?: return null
        LOG.info("Found declaration descriptor {}", target)

        val destination = getInitialLocation(target) ?: return null

        if (isInsideArchive(destination.uri)) {
            handleArchiveSource(target, destination)
        }

        return destination
    }

    private fun getInitialLocation(target: DeclarationDescriptor): Location? {
        var destination = location(target)

        val psi = target.findPsi()
        if (psi is KtNamedDeclaration) {
            destination = psi.nameIdentifier?.let(::location) ?: destination
        }

        return destination
    }

    private fun handleArchiveSource(target: DeclarationDescriptor, destination: Location) {
        parseURI(destination.uri).toKlsURI()?.let { klsURI ->
            val (klsSourceURI, content) = classContentProvider.contentOf(klsURI)

            destination.uri =
                if (config.useKlsScheme) {
                    klsSourceURI.toString()
                } else {
                    getOrCreateTempFile(klsSourceURI, content).toUri().toString()
                }

            if (destination.range.isZero) {
                updateRangeFromContent(target, content, destination)
            }
        }
    }

    private fun getOrCreateTempFile(klsSourceURI: KlsURI, content: String): Path {
        return cachedTempFiles[klsSourceURI]
            ?: run {
                val (name, extension) = getFileNameParts(klsSourceURI)
                tempDir.createTempFile(name, extension).also {
                    it.toFile().writeText(content)
                    cachedTempFiles[klsSourceURI] = it
                }
            }
    }

    private fun getFileNameParts(klsSourceURI: KlsURI): Pair<String, String> {
        val name = klsSourceURI.fileName.partitionAroundLast(".").first
        val extensionWithoutDot = klsSourceURI.fileExtension
        val extension = if (extensionWithoutDot != null) ".$extensionWithoutDot" else ""
        return Pair(name, extension)
    }

    private fun updateRangeFromContent(
        target: DeclarationDescriptor,
        content: String,
        destination: Location,
    ) {
        val name =
            when (target) {
                is ConstructorDescriptor -> target.constructedClass.name.toString()
                else -> target.name.toString()
            }

        definitionPattern
            .findAll(content)
            .map { it.groups[1]!! }
            .find { it.value == name }
            ?.range
            ?.let { range ->
                destination.range =
                    Range(position(content, range.first), position(content, range.last))
            }
    }

    private fun isInsideArchive(uri: String): Boolean =
        uri.contains(".jar!") ||
            uri.contains(".zip!") ||
            compilerClassPath.javaHome?.let {
                Paths.get(parseURI(uri)).toString().startsWith(File(it).path)
            } ?: false
}
