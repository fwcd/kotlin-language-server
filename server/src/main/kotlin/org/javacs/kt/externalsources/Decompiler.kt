package org.javacs.kt.externalsources

import java.nio.file.Path

interface Decompiler : SourceArchiveProvider {
	fun decompileClass(compiledClass: Path): Path

	fun decompileJar(compiledJar: Path): Path

	override fun fetchSourceArchive(compiledArchive: Path) = decompileJar(compiledArchive)
}
