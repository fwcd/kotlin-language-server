package org.javacs.kt

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.name.FqName
import org.javacs.kt.compiler.Compiler

/**
 * A global view of all available symbols across all packages.
 */
class SymbolIndex {
    val globalDescriptors: MutableSet<DeclarationDescriptor> = mutableSetOf()

    private fun update(module: ModuleDescriptor) {
        val started = System.currentTimeMillis()
        LOG.info("Updating symbol index...")

        globalDescriptors += allDescriptors(module)

        val finished = System.currentTimeMillis()
        LOG.info("Updated symbol index in ${finished - started} ms!")
    }

    private fun allDescriptors(module: ModuleDescriptor): Collection<DeclarationDescriptor> = allPackages(module)
        .map(module::getPackage)
        .flatMap { it.memberScope.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER) }

    private fun allPackages(module: ModuleDescriptor, pkgName: FqName = FqName.ROOT): Collection<FqName> = module
        .getSubPackagesOf(pkgName) { true }
        .flatMap { allPackages(module, it) }
}
