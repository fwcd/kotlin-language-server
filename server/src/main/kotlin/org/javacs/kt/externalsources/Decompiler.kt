package org.javacs.kt.externalsources

import java.nio.file.Path

interface Decompiler : SourceJarProvider {
	fun decompileClass(compiledClass: Path): Path

	fun decompileJar(compiledJar: Path): Path

	override fun fetchSourceJar(compiledJar: Path) = decompileJar(compiledJar)
}
