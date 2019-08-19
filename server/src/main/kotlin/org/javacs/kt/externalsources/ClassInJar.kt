package org.javacs.kt.externalsources

import java.io.File
import java.nio.file.Path
import java.net.URI
import java.nio.file.Paths
import java.nio.file.Files
import java.util.jar.JarFile
import org.javacs.kt.LOG
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.replaceExtensionWith

fun parseUriAsClassInJar(uri: String): ClassInJar {
	val splitUri = uri.split(".jar!")
	val jarUri = splitUri[0] + ".jar"
	val jarPath = Paths.get(URI.create(jarUri))
	val relativeClassPath = Paths.get(splitUri[1].trimLeadingPathSeparator())

	val relativeJavaPath = Paths.get((splitUri[1].split(".class")[0] + ".java").trimLeadingPathSeparator())

	return ClassInJar(jarPath, relativeClassPath, relativeJavaPath)
}

class ClassInJar(
	val jarPath: Path,
	val relativeClassPath: Path,
	val relativeJavaPath: Path
) {
	val className: String
	private val classExtension: String

	val javaName: String
	private val javaExtension: String

	init {
		var parsedName = relativeClassPath.fileName.parseName()
		className = parsedName.name
		classExtension = parsedName.extension

		parsedName = relativeJavaPath.fileName.parseName()
		javaName = parsedName.name
		javaExtension = parsedName.extension
	}

	fun toSourceClass(provider: SourceJarProvider): ClassInJar {
		val sourceJarPath = provider.fetchSourceJar(jarPath)
		// Need to decided what to pass in for 3rd param
		return ClassInJar(sourceJarPath, relativeClassPath.replaceExtensionWith(".java"), relativeClassPath.replaceExtensionWith(".java"))
	}

	fun decompile(decompiler: Decompiler) = extractJava() ?: decompiler.decompileClass(extractClass())

	fun extractJava(): Path? {
		return JarFile(jarPath.toFile()).use { jarFile ->
			val jarEntry = jarFile.entries().asSequence()
					.filter { Paths.get(it.name).equals(relativeJavaPath) }
					.firstOrNull()

			if (jarEntry == null) {
                LOG.warn("Could not find {}", javaName)
				return null
			}

			val tmpDir = System.getProperty("java.io.tmpdir")

			File("$tmpDir/${relativeJavaPath.parent}/").mkdirs()
			val tmpFile = File("$tmpDir/${relativeJavaPath.parent}/$javaName$javaExtension")
			tmpFile.createNewFile()
			tmpFile.deleteOnExit() // Make sure the extracted file is deleted upon exit
			tmpFile.outputStream().use { jarFile.getInputStream(jarEntry).copyTo(it) }
			Paths.get(tmpFile.absolutePath)
		}
	}

	fun extractClass(): Path {
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
