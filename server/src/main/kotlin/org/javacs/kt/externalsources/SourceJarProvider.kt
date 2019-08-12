package org.javacs.kt.externalsources

import java.nio.file.Path

interface SourceJarProvider {
    fun fetchSourceJar(compiledJar: Path): Path
}
