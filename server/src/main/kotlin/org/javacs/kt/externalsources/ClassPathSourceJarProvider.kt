package org.javacs.kt.externalsources

import org.javacs.kt.CompilerClassPath
import java.nio.file.Path

class ClassPathSourceJarProvider(
    private val cp: CompilerClassPath
) : SourceJarProvider {
    override fun fetchSourceJar(compiledJar: Path): Path? =
        cp.classPath.firstOrNull { it.compiledJar == compiledJar }?.sourceJar
}
