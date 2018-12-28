package org.javacs.kt.externalsources

import java.nio.file.Path
import java.nio.file.Files
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.replaceExtensionWith
import org.javacs.kt.util.withCustomStdout
import org.javacs.kt.LOG
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler

class FernflowerDecompiler : Decompiler {
	private val outputDir by lazy(::createOutputDirectory)

	override fun decompileClass(compiledClass: Path) = decompile(compiledClass, ".java")

	override fun decompileJar(compiledJar: Path) = decompile(compiledJar, ".jar")

	fun decompile(compiledClassOrJar: Path, newFileExtension: String): Path {
		invokeDecompiler(compiledClassOrJar, outputDir)
		val srcOutName = compiledClassOrJar.fileName.replaceExtensionWith(newFileExtension)
		val srcOutPath = outputDir.resolve(srcOutName)

		if (!Files.exists(srcOutPath)) {
			throw KotlinLSException("Could not decompile ${compiledClassOrJar.fileName}: Fernflower did not generate sources at ${srcOutPath.fileName}")
		}

		return srcOutPath
	}

	private fun invokeDecompiler(input: Path, output: Path) {
		LOG.info("Decompiling {} using Fernflower...", input.fileName)
		withCustomStdout(LOG.outStream) {
			ConsoleDecompiler.main(arrayOf(input.toString(), output.toString()))
		}
	}

	private fun createOutputDirectory(): Path {
		val out = Files.createTempDirectory("fernflowerOut")
		Runtime.getRuntime().addShutdownHook(Thread {
			// Deletes the output directory and all contained (decompiled)
			// JARs when the JVM terminates
			out.toFile().deleteRecursively()
		})
		return out
	}
}
