package org.javacs.kt.implementation

import org.eclipse.lsp4j.Location
import org.javacs.kt.CompiledFile
import org.javacs.kt.SourceFiles
import org.javacs.kt.SourcePath
import org.javacs.kt.LOG
import org.javacs.kt.position.location
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassOrAny
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces


fun findImplementation(
    sp: SourcePath,
    sf: SourceFiles,
    file: CompiledFile,
    cursor: Int
): List<Location> {
    val (_, target) = file.referenceExpressionAtPoint(cursor) ?: return emptyList()

    LOG.info("Finding implementations for declaration descriptor {}", target)

    return when (target) {
        is ClassDescriptor -> findImplementations(sp, sf, file, target)
        else -> emptyList()
    }
}

private fun findImplementations(sp: SourcePath, sf: SourceFiles, file: CompiledFile, descriptor: ClassDescriptor): List<Location> {
    val implementations = mutableListOf<Location>()

    // Get all Kotlin file URIs by scanning workspace roots
    val allUris = sf.getAllUris()
    if (descriptor.kind == ClassKind.INTERFACE) {
        // Find all classes that implement this interface
        allUris.forEach { uri ->
            val ktFile = sp.parsedFile(uri)
            ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { ktClass ->
                val classDesc = file.compile.get(BindingContext.CLASS, ktClass)
                if (classDesc != null && descriptor in classDesc.getSuperInterfaces()) {
                    location(ktClass)?.let { implementations.add(it) }
                }
            }
        }
    } else if (descriptor.kind == ClassKind.CLASS) {
        // Find all subclasses
        allUris.forEach { uri ->
            val ktFile = sp.parsedFile(uri)
            ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { ktClass ->
                val classDesc = file.compile.get(BindingContext.CLASS, ktClass)
                if (classDesc?.getSuperClassOrAny() == descriptor) {
                    location(ktClass)?.let { implementations.add(it) }
                }
            }
        }
    }

    return implementations
}

