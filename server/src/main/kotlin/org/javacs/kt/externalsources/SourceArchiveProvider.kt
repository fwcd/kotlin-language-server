package org.javacs.kt.externalsources

import java.nio.file.Path

interface SourceArchiveProvider {
    fun fetchSourceArchive(compiledArchive: Path): Path?
}

class CompositeSourceArchiveProvider(val lhs: SourceArchiveProvider, val rhs: SourceArchiveProvider) : SourceArchiveProvider {
    override fun fetchSourceArchive(compiledArchive: Path): Path? =
        lhs.fetchSourceArchive(compiledArchive) ?: rhs.fetchSourceArchive(compiledArchive)
}
