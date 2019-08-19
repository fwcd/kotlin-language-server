package org.javacs.kt.definition

import org.eclipse.lsp4j.Location
import java.net.URI
import org.javacs.kt.CompiledFile
import org.javacs.kt.LOG
import org.javacs.kt.URIConfiguration
import org.javacs.kt.externalsources.Decompiler
import org.javacs.kt.externalsources.toKlsJarURI
import org.javacs.kt.position.location
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtNamedDeclaration

fun goToDefinition(file: CompiledFile, cursor: Int, decompiler: Decompiler, config: URIConfiguration): Location? {
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
            val klsURI = URI(rawClassURI).toKlsJarURI() ?: return null
            if (config.useKlsScheme) {
                // Defer decompilation until a jarClassContents request is sent
                destination.uri = klsURI.toString()
            } else {
                // Decompile immediately and return the path to a temporary file
                // since the client has not opted into KLS URIs
                destination.uri = klsURI.extractAndDecompile(decompiler).toUri().toString()
            }
        }
    }

    return destination
}

private fun isInsideJar(uri: String) = uri.contains(".jar!")
