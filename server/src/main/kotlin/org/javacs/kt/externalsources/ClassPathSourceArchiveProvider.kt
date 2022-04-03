package org.javacs.kt.externalsources

import org.javacs.kt.CompilerClassPath
import java.nio.file.Path

class ClassPathSourceArchiveProvider(
    private val cp: CompilerClassPath
) : SourceArchiveProvider {
    override fun fetchSourceArchive(compiledArchive: Path): Path? =
        cp.classPath.firstOrNull { it.compiledJar == compiledArchive }?.sourceJar
}
