package org.javacs.kt

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.FqName

/**
 * A global view of all available symbols across all packages.
 */
class SymbolIndex(private val module: ModuleDescriptor) {
    private fun allPackages(pkgName: FqName = FqName.ROOT): Collection<FqName> = module
        .getSubPackagesOf(pkgName) { true }
        .flatMap(::allPackages)
}
