package org.javacs.kt.implementation

import org.eclipse.lsp4j.Location
import org.javacs.kt.LOG
import org.javacs.kt.SourcePath
import org.javacs.kt.CompiledFile
import org.javacs.kt.position.location
import org.javacs.kt.util.findParent
import org.javacs.kt.util.toPath
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import java.nio.file.Path

/** Finds implementations of interfaces, abstract classes, or their members. */
fun findImplementations(file: Path, cursor: Int, sp: SourcePath): List<Location> {
    val compiled = sp.currentVersion(file.toUri())
    val target = compiled.referenceExpressionAtPoint(cursor)?.second
        ?: compiled.elementAtPoint(cursor)
            ?.findParent<KtNamedDeclaration>()
            ?.let { compiled.compile[BindingContext.DECLARATION_TO_DESCRIPTOR, it] }
        ?: return emptyList()

    LOG.info("Finding implementations of {}", target)

    return when (target) {
        is ClassDescriptor -> findClassImplementations(target, sp)
        is FunctionDescriptor -> findFunctionImplementations(target, sp)
        is PropertyDescriptor -> findPropertyImplementations(target, sp)
        else -> emptyList()
    }
}

private fun findClassImplementations(target: ClassDescriptor, sp: SourcePath): List<Location> {
    if (target.modality == Modality.FINAL) return emptyList()

    val targetFqName = target.fqNameSafe
    val sourceFiles = sp.all().map { it.toPath() }
    val context = sp.compileFiles(sourceFiles.map(Path::toUri))

    return context.getSliceContents(BindingContext.CLASS)
        .values
        .filter { classDescriptor ->
            classDescriptor != target
            && classDescriptor.kind != ClassKind.INTERFACE
            && classDescriptor.modality != Modality.ABSTRACT
            && classDescriptor.getAllSuperClassifiers().any { it.fqNameSafe == targetFqName }
        }
        .mapNotNull { locationOfClassIdentifier(it) }
        .sortedWith(compareBy({ it.uri }, { it.range.start.line }))
}

private fun findFunctionImplementations(target: FunctionDescriptor, sp: SourcePath): List<Location> {
    val containingClass = target.containingDeclaration as? ClassDescriptor ?: return emptyList()
    if (containingClass.modality == Modality.FINAL) return emptyList()

    val targetFqName = containingClass.fqNameSafe
    val targetName = target.name
    val sourceFiles = sp.all().map { it.toPath() }
    val context = sp.compileFiles(sourceFiles.map(Path::toUri))

    return context.getSliceContents(BindingContext.CLASS)
        .values
        .filter { classDescriptor ->
            classDescriptor != containingClass
            && classDescriptor.getAllSuperClassifiers().any { it.fqNameSafe == targetFqName }
        }
        .flatMap { classDescriptor ->
            classDescriptor.defaultType.memberScope.getContributedDescriptors()
                .filterIsInstance<FunctionDescriptor>()
                .filter { fn ->
                    fn.name == targetName
                    && fn.overriddenDescriptors.any { overridden ->
                        overridden.fqNameSafe == target.fqNameSafe
                        || overridden.original.fqNameSafe == target.fqNameSafe
                    }
                }
        }
        .mapNotNull { locationOfIdentifier(it) }
        .sortedWith(compareBy({ it.uri }, { it.range.start.line }))
}

private fun findPropertyImplementations(target: PropertyDescriptor, sp: SourcePath): List<Location> {
    val containingClass = target.containingDeclaration as? ClassDescriptor ?: return emptyList()
    if (containingClass.modality == Modality.FINAL) return emptyList()

    val targetFqName = containingClass.fqNameSafe
    val targetName = target.name
    val sourceFiles = sp.all().map { it.toPath() }
    val context = sp.compileFiles(sourceFiles.map(Path::toUri))

    return context.getSliceContents(BindingContext.CLASS)
        .values
        .filter { classDescriptor ->
            classDescriptor != containingClass
            && classDescriptor.getAllSuperClassifiers().any { it.fqNameSafe == targetFqName }
        }
        .flatMap { classDescriptor ->
            classDescriptor.defaultType.memberScope.getContributedDescriptors()
                .filterIsInstance<PropertyDescriptor>()
                .filter { prop ->
                    prop.name == targetName
                    && prop.overriddenDescriptors.any { overridden ->
                        overridden.fqNameSafe == target.fqNameSafe
                        || overridden.original.fqNameSafe == target.fqNameSafe
                    }
                }
        }
        .mapNotNull { locationOfIdentifier(it) }
        .sortedWith(compareBy({ it.uri }, { it.range.start.line }))
}

private fun locationOfClassIdentifier(descriptor: ClassDescriptor): Location? {
    val psi = descriptor.findPsi()
    if (psi is KtNamedDeclaration) {
        return psi.nameIdentifier?.let(::location) ?: location(psi)
    }
    return location(descriptor)
}

private fun locationOfIdentifier(descriptor: DeclarationDescriptor): Location? {
    val psi = descriptor.findPsi()
    if (psi is KtNamedDeclaration) {
        return psi.nameIdentifier?.let(::location) ?: location(psi)
    }
    return location(descriptor)
}

private fun ClassDescriptor.getAllSuperClassifiers(): Sequence<ClassDescriptor> = sequence {
    val visited = mutableSetOf<ClassDescriptor>()
    val queue = ArrayDeque<ClassDescriptor>()

    for (supertype in this@getAllSuperClassifiers.typeConstructor.supertypes) {
        (supertype.constructor.declarationDescriptor as? ClassDescriptor)?.let { queue.add(it) }
    }

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (!visited.add(current)) continue
        yield(current)
        for (supertype in current.typeConstructor.supertypes) {
            (supertype.constructor.declarationDescriptor as? ClassDescriptor)?.let { queue.add(it) }
        }
    }
}
