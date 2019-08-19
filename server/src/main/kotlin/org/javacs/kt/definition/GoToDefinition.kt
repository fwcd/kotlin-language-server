package org.javacs.kt.definition

import org.eclipse.lsp4j.Location
import java.net.URI
import java.io.File
import org.javacs.kt.CompiledFile
import org.javacs.kt.LOG
import org.javacs.kt.ExternalSourcesConfiguration
import org.javacs.kt.externalsources.JarClassContentProvider
import org.javacs.kt.externalsources.toKlsURI
import org.javacs.kt.position.location
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtNamedDeclaration

fun goToDefinition(file: CompiledFile, cursor: Int, jarClassContentProvider: JarClassContentProvider, config: ExternalSourcesConfiguration): Location? {
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
            URI(rawClassURI).toKlsURI()?.let { klsURI ->
                val (klsSourceURI, contents) = jarClassContentProvider.contentOf(klsURI)

                if (config.useKlsScheme) {
                    // Defer decompilation until a jarClassContents request is sent
                    destination.uri = klsSourceURI.toString()
                } else {
                    // Return the path to a temporary file
                    // since the client has not opted into KLS URIs
                    val tmpFile = File.createTempFile("decompiled", ".${klsSourceURI}")
                    tmpFile.deleteOnExit()
                    tmpFile.writeText(contents)
                    destination.uri = tmpFile.toURI().toString()
                }
            }
        }
    }

    return destination
}

private fun isInsideJar(uri: String) = uri.contains(".jar!")
