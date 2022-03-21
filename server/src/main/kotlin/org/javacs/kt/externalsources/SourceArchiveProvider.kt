package org.javacs.kt.externalsources

import java.nio.file.Path

interface SourceArchiveProvider {
    fun fetchSourceArchive(compiledArchive: Path): Path?
}
