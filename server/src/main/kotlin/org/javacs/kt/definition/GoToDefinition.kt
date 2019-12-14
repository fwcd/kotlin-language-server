package org.javacs.kt.definition

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Range
import java.net.URI
import java.nio.file.Path
import java.nio.file.Files
import org.javacs.kt.CompiledFile
import org.javacs.kt.LOG
import org.javacs.kt.ExternalSourcesConfiguration
import org.javacs.kt.externalsources.JarClassContentProvider
import org.javacs.kt.externalsources.toKlsURI
import org.javacs.kt.externalsources.KlsURI
import org.javacs.kt.position.location
import org.javacs.kt.position.isZero
import org.javacs.kt.position.position
import org.javacs.kt.util.partitionAroundLast
import org.javacs.kt.util.TemporaryDirectory
import org.javacs.kt.util.parseURI
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor

private val cachedTempFiles = mutableMapOf<KlsURI, Path>()
private val definitionPattern = Regex("(?:class|interface|object|fun)\\s+(\\w+)")

fun goToDefinition(
    file: CompiledFile,
    cursor: Int,
    jarClassContentProvider: JarClassContentProvider,
    tempDir: TemporaryDirectory,
    config: ExternalSourcesConfiguration
): Location? {
    val (_, target) = file.referenceAtPoint(cursor) ?: return null

    LOG.info("Found declaration descriptor {}", target)
    var destination = location(target)
    val psi = target.findPsi()

    if (psi is KtNamedDeclaration) {
        destination = psi.nameIdentifier?.let(::location) ?: destination
    }

    if (destination != null) {
        val rawClassURI = destination.uri

        if (isInsideJar(rawClassURI)) {
            parseURI(rawClassURI).toKlsURI()?.let { klsURI ->
                val (klsSourceURI, content) = jarClassContentProvider.contentOf(klsURI)

                if (config.useKlsScheme) {
                    // Defer decompilation until a jarClassContents request is sent
                    destination.uri = klsSourceURI.toString()
                } else {
                    // Return the path to a temporary file
                    // since the client has not opted into
                    // or does not support KLS URIs
                    val tmpFile = cachedTempFiles[klsSourceURI] ?: run {
                        val (name, extension) = klsSourceURI.fileName.partitionAroundLast(".")
                        tempDir.createTempFile(name, extension)
                            .also {
                                it.toFile().writeText(content)
                                cachedTempFiles[klsSourceURI] = it
                            }
                    }

                    destination.uri = tmpFile.toUri().toString()
                }

                if (destination.range.isZero) {
                    // Try to find the definition inside the source directly
                    val name = when (target) {
                        is ConstructorDescriptor -> target.constructedClass.name.toString()
                        else -> target.name.toString()
                    }
                    definitionPattern.findAll(content)
                        .map { it.groups[1]!! }
                        .find { it.value == name }
                        ?.let { it.range }
                        ?.let { destination.range = Range(position(content, it.first), position(content, it.last)) }
                }
            }
        }
    }

    return destination
}

private fun isInsideJar(uri: String) = uri.contains(".jar!")
