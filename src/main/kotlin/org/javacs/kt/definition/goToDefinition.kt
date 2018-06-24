package org.javacs.kt.definition

import org.eclipse.lsp4j.Location
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.location
import org.javacs.kt.util.KotlinLSException
import java.util.jar.JarFile
import java.net.URI
import java.nio.file.Paths
import java.nio.file.Files
import java.io.File

private val LOG = LoggerFactory.getLogger("org.javacs.kt.definition.GoToDefinitionKt")

fun goToDefinition(file: CompiledFile, cursor: Int): Location? {
    val (_, target) = file.referenceAtPoint(cursor) ?: return null
    // TODO go to declaration name rather than beginning of javadoc comment
    LOG.info("Found declaration descriptor {}", target)
    val destination = location(target)

    if (destination != null) {
        val rawURI = destination.uri
        if (isInsideJar(rawURI)) {
            destination.uri = readCompiledClassToTemporaryFile(rawURI)
        }
    }

    return destination
}

private fun isInsideJar(uri: String) = uri.contains(".jar!")

private fun readCompiledClassToTemporaryFile(uriWithJar: String): String {
    val splittedUri = uriWithJar.split(".jar!")
    val jarUri = splittedUri[0] + ".jar"
    val jarPath = Paths.get(URI.create(jarUri))
    val classInJarPath = Paths.get(trimLeadingPathSeparator(splittedUri[1]))
    val className = classInJarPath.fileName.toString().trimSuffixIfPresent(".class")

    JarFile(jarPath.toFile()).use { jarFile ->
        for (jarEntry in jarFile.entries()) {
            val jarEntryPath = Paths.get(jarEntry.name)

            if (classInJarPath.equals(jarEntryPath)) {
                // Found the correct class inside of the JAR file
                val tmp = Files.createTempFile(className, ".class").toFile()
                tmp.deleteOnExit() // Make sure the file is deleted upon exit

                tmp.outputStream().use {
                    jarFile.getInputStream(jarEntry).copyTo(it)
                }

                return tmp.toURI().toString()
            }
        }
    }

    throw KotlinLSException("Could not find $classInJarPath in ${jarPath.fileName}")
}

private fun trimLeadingPathSeparator(path: String): String {
    val firstChar = path[0]
    return if (firstChar == '/' || firstChar == '\\') path.substring(1) else path
}

private fun String.trimSuffixIfPresent(suffix: String) =
        if (endsWith(suffix)) substring(0, length - suffix.length) else this
