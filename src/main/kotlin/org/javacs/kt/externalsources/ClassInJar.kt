package org.javacs.kt.externalsources

import java.nio.file.Path
import java.net.URI
import java.nio.file.Paths
import java.nio.file.Files
import java.util.jar.JarFile
import org.javacs.kt.LOG
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.replaceExtensionWith

fun parseUriAsClassInJar(uri: String): ClassInJar {
	val splittedUri = uri.split(".jar!")
	val jarUri = splittedUri[0] + ".jar"
	val jarPath = Paths.get(URI.create(jarUri))
	val relativeClassPath = Paths.get(splittedUri[1].trimLeadingPathSeparator())

	return ClassInJar(jarPath, relativeClassPath)
}

class ClassInJar(
	val jarPath: Path,
	val relativeClassPath: Path
) {
	val className: String
	private val classExtension: String

	init {
		val parsedName = relativeClassPath.fileName.parseName()
		className = parsedName.name
		classExtension = parsedName.extension
	}

	fun toSourceClass(provider: SourceJarProvider): ClassInJar {
		val sourceJarPath = provider.fetchSourceJar(jarPath)
		return ClassInJar(sourceJarPath, relativeClassPath.replaceExtensionWith(".java"))
	}

	fun decompile(decompiler: Decompiler) = decompiler.decompileClass(extract())

	fun extract(): Path {
		return JarFile(jarPath.toFile()).use { jarFile ->
			val jarEntry = jarFile.entries().asSequence()
					.filter { Paths.get(it.name).equals(relativeClassPath) }
					.firstOrNull()

			if (jarEntry == null) {
				throw KotlinLSException("Could not find $className in ${jarPath.fileName}")
			}

			val tmpPath = Files.createTempFile(className, classExtension)
			val tmpFile = tmpPath.toFile()
			tmpFile.deleteOnExit() // Make sure the extracted file is deleted upon exit
			tmpFile.outputStream().use { jarFile.getInputStream(jarEntry).copyTo(it) }
			tmpPath
		}
	}
}

private data class FileName(val name: String, val extension: String)

private fun String.trimLeadingPathSeparator(): String {
    val firstChar = this[0]
    return if (firstChar == '/' || firstChar == '\\') substring(1) else this
}

private fun Path.parseName(): FileName {
	val pathStr = toString()
	val dotPos = pathStr.lastIndexOf(".")
	return FileName(pathStr.substring(0, dotPos), pathStr.substring(dotPos))
}
