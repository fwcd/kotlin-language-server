package org.javacs.kt.definition

import org.eclipse.lsp4j.Location
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.location
import org.javacs.kt.LOG
import org.javacs.kt.externalsources.parseUriAsClassInJar
import org.javacs.kt.externalsources.ClassInJar
import org.javacs.kt.externalsources.Decompiler
import org.javacs.kt.externalsources.FernflowerDecompiler
import org.javacs.kt.util.replaceExtensionWith

private val decompiler: Decompiler = FernflowerDecompiler()
private val decompiledClassesCache = mutableMapOf<String, String>()

fun goToDefinition(file: CompiledFile, cursor: Int): Location? {
    val (_, target) = file.referenceAtPoint(cursor) ?: return null
    // TODO go to declaration name rather than beginning of javadoc comment
    LOG.info("Found declaration descriptor $target")
    val destination = location(target)
    
    // FIXME: Go to definition in decompiled files is temporarily
    //        disabled until https://github.com/fwcd/KotlinLanguageServer/issues/45
    //        is resolved.
    
    // if (destination != null) {
    //     val rawClassURI = destination.uri
    //     if (isInsideJar(rawClassURI)) {
    //         destination.uri = cachedDecompile(rawClassURI)
    //     }
    // }
    
    return destination
}

private fun isInsideJar(uri: String) = uri.contains(".jar!")

private fun cachedDecompile(uri: String) = decompiledClassesCache[uri] ?: decompile(uri)

private fun decompile(uri: String): String {
    val decompiledUri = parseUriAsClassInJar(uri)
            .decompile(decompiler)
            .toUri()
            .toString()
    decompiledClassesCache[uri] = decompiledUri
    return decompiledUri
}
