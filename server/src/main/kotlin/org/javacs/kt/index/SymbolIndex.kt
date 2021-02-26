package org.javacs.kt.index

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.name.FqName
import org.javacs.kt.compiler.Compiler
import org.javacs.kt.LOG
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A global view of all available symbols across all packages.
 */
class SymbolIndex {
    val globalDescriptors = ConcurrentHashMap<FqName, DeclarationDescriptor>()

    fun update(module: ModuleDescriptor) {
        val started = System.currentTimeMillis()
        LOG.info("Updating symbol index...")

        try {
            for (descriptor in allDescriptors(module)) {
                globalDescriptors[descriptor.fqNameSafe] = descriptor
            }

            val finished = System.currentTimeMillis()
            LOG.info("Updated symbol index in ${finished - started} ms! (${globalDescriptors.size} symbol(s))")
        } catch (e: Exception) {
            LOG.error("Error while updating symbol index")
            LOG.printStackTrace(e)
        }
    }

    private fun allDescriptors(module: ModuleDescriptor): Collection<DeclarationDescriptor> = allPackages(module)
        .map(module::getPackage)
        .flatMap {
            try {
                it.memberScope.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER)
            } catch (e: IllegalStateException) {
                LOG.warn("Could not query descriptors in package $it")
                emptyList()
            }
        }

    private fun allPackages(module: ModuleDescriptor, pkgName: FqName = FqName.ROOT): Collection<FqName> = module
        .getSubPackagesOf(pkgName) { it.toString() != "META-INF" }
        .flatMap { setOf(it) + allPackages(module, it) }
}
