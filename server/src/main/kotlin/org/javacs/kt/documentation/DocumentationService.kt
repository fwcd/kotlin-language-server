package org.javacs.kt.documentation

import org.javacs.kt.classpath.ClassPathEntry
import org.javacs.kt.util.AsyncExecutor
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Paths
import org.javacs.kt.compiler.Compiler
import org.jetbrains.kotlin.backend.common.serialization.findPackage
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedSimpleFunctionDescriptor
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import java.nio.file.Path
import java.util.jar.JarInputStream
import kotlin.io.path.inputStream

class DocumentationService(
    private val compiler: Compiler,
    private val classpath: List<ClassPathEntry>,
) : AutoCloseable {

    private val async = AsyncExecutor()

    private fun parse(
        content: String,
        fileName: String,
    ): KtFile = compiler.createKtFile(
        content = content.replace("\r\n", "\n"),
        file = Paths.get(fileName)
    )

    private fun <T : Any> Path.processJar(
        entryHandler: (fileName: String, fileReader: () -> KtFile) -> T?,
    ): List<T> = inputStream().use { stream ->
        val jis = JarInputStream(stream)
        generateSequence { jis.nextJarEntry }
            .mapNotNull {
                val fileName = it.name
                entryHandler(fileName) {
                    val bytes = jis.readBytes()
                    parse(content = String(bytes), fileName = fileName)
                }
            }
            .toList()
    }

    private val indexJob = async.compute {
        classpath
            .asSequence()
            .filterNot { Thread.currentThread().isInterrupted }
            .mapNotNull { it.sourceJar }
            .flatMap { sourceJar ->
                sourceJar.processJar { name, reader ->
                    when {
                        !name.endsWith(".kt") -> null
                        else -> {
                            val file = reader()
                            SourceJarInfo(
                                packageName = file.packageFqName.asString(),
                                sourceJar = sourceJar,
                                fileName = name,
                                functions = file.findChildrenByClass(KtNamedFunction::class.java)
                                    .mapNotNull { it.name },
                                types = file.findChildrenByClass(KtClassOrObject::class.java)
                                    .mapNotNull { it.name },
                            )
                        }
                    }
                }
            }
            .groupBy { it.packageName }
    }

    fun findFunction(target: DeserializedSimpleFunctionDescriptor): KtNamedFunction? {
        val type = target.parents.firstIsInstanceOrNull<DeserializedClassDescriptor>() ?: return null

        return findClass(type)
            ?.collectDescendantsOfType<KtNamedFunction> {
                it.name == target.name.asString() &&
                    it.valueParameters.size == target.valueParameters.size
            }
            ?.firstOrNull()
    }

    fun findClass(target: DeserializedClassDescriptor): KtClassOrObject? {
        val potentialFiles: List<SourceJarInfo> = target.findPackage().findFiles()
        val targetFile = potentialFiles.firstOrNull { target.name.asString() in it.types } ?: return null
        val ktFile = targetFile.sourceJar.processJar { fileName, fileReader ->
            if (fileName != targetFile.fileName) null
            else fileReader()
        }.firstOrNull() ?: return null

        return ktFile.findDescendantOfType { it.name == target.name.asString() }
    }

    fun PackageFragmentDescriptor.findFiles(): List<SourceJarInfo> =
        indexJob.get()[fqNameSafe.asString()] ?: emptyList()

    override fun close() {
        indexJob.cancel(true)
    }
}
