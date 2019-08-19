package org.javacs.kt.externalsources

import java.nio.file.Path

class CachingDecompiler(private val wrapped: Decompiler) : Decompiler {
    private val cache = mutableMapOf<Path, Path>()

    override fun decompileClass(compiledClass: Path): Path =
        cache[compiledClass] ?: wrapped.decompileClass(compiledClass)
            .also { cache[compiledClass] = it }

    override fun decompileJar(compiledJar: Path): Path =
        cache[compiledJar] ?: wrapped.decompileJar(compiledJar)
            .also { cache[compiledJar] = it }
}
