package org.javacs.kt.externalsources

import java.nio.file.Files
import java.nio.file.Path
import org.javacs.kt.LOG
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.replaceExtensionWith
import org.javacs.kt.util.withCustomStdout
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler

class FernFlowerDecompiler : Decompiler {
    private val outputDir: Path by lazy {
        Files.createTempDirectory("fernflowerOut").also {
            Runtime.getRuntime().addShutdownHook(Thread { it.toFile().deleteRecursively() })
        }
    }

    private val decompilerOptions =
        arrayOf(
            "-iec=1", // Include entire classpath for better context
            "-jpr=1", // Include parameter names in method signatures
        )

    override fun decompileClass(compiledClass: Path): Path {
        return decompile(compiledClass, ".java")
    }

    override fun decompileJar(compiledJar: Path): Path {
        return decompile(compiledJar, ".jar")
    }

    private fun decompile(input: Path, extension: String): Path {
        LOG.info("Decompiling ${input.fileName} using FernFlower...")

        val args = decompilerOptions + arrayOf(input.toString(), outputDir.toString())

        withCustomStdout(LOG.outStream) { ConsoleDecompiler.main(args) }

        val outName = input.fileName.replaceExtensionWith(extension)
        val outPath = outputDir.resolve(outName)
        if (!Files.exists(outPath)) {
            throw KotlinLSException(
                "Could not decompile ${input.fileName}: FernFlower did not generate sources at $outName"
            )
        }
        return outPath
    }
}
