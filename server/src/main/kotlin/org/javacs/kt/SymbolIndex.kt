package org.javacs.kt

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.name.FqName
import org.javacs.kt.compiler.Compiler
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A global view of all available symbols across all packages.
 */
class SymbolIndex {
    private val globalDescriptors: MutableSet<DeclarationDescriptor> = mutableSetOf()
    private val lock = ReentrantLock()

    fun update(module: ModuleDescriptor) {
        val started = System.currentTimeMillis()
        LOG.info("Updating symbol index...")

        try {
            val foundDescriptors = allDescriptors(module)
            lock.withLock {
                globalDescriptors += foundDescriptors
            }

            val finished = System.currentTimeMillis()
            LOG.info("Updated symbol index in ${finished - started} ms! (${globalDescriptors.size} symbol(s))")
        } catch (e: Exception) {
            LOG.warn("Could not update symbol index: $e")
        }
    }

    fun <T> withGlobalDescriptors(action: (Set<DeclarationDescriptor>) -> T): T = lock.withLock { action(globalDescriptors) }

    private fun allDescriptors(module: ModuleDescriptor): Collection<DeclarationDescriptor> = allPackages(module)
        .map(module::getPackage)
        .flatMap { it.memberScope.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER) }

    private fun allPackages(module: ModuleDescriptor, pkgName: FqName = FqName.ROOT): Collection<FqName> = module
        .getSubPackagesOf(pkgName) { it.toString() != "META-INF" }
        .flatMap { setOf(it) + allPackages(module, it) }
}
