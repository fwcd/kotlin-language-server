package org.javacs.kt.index

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi2ir.intermediate.extensionReceiverType
import org.javacs.kt.LOG
import org.javacs.kt.progress.Progress
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.insert

private val MAX_FQNAME_LENGTH = 255
private val MAX_SHORT_NAME_LENGTH = 80

private object Symbols : Table() {
    val fqName = varchar("fqname", length = MAX_FQNAME_LENGTH) references FqNames.fqName
    val kind = integer("kind")
    val visibility = integer("visibility")
    val extensionReceiverType = varchar("extensionreceivertype", length = MAX_FQNAME_LENGTH).nullable()

    override val primaryKey = PrimaryKey(fqName)
}

private object FqNames : Table() {
    val fqName = varchar("fqname", length = MAX_FQNAME_LENGTH)
    val shortName = varchar("shortname", length = MAX_SHORT_NAME_LENGTH)

    override val primaryKey = PrimaryKey(fqName)
}

/**
 * A global view of all available symbols across all packages.
 */
class SymbolIndex {
    private val db = Database.connect("jdbc:h2:mem:symbolindex;DB_CLOSE_DELAY=-1", "org.h2.Driver")

    var progressFactory: Progress.Factory = Progress.Factory.None

    init {
        transaction(db) {
            SchemaUtils.create(Symbols, FqNames)
        }
    }

    /** Rebuilds the entire index. May take a while. */
    fun refresh(module: ModuleDescriptor) {
        val started = System.currentTimeMillis()
        LOG.info("Updating symbol index...")

        progressFactory.create("Indexing").thenApply { progress ->
            try {
                val descriptors = allDescriptors(module)

                // TODO: Incremental updates
                transaction(db) {
                    Symbols.deleteAll()

                    for (descriptor in descriptors) {
                        val descriptorFqn = descriptor.fqNameSafe
                        val extensionReceiverFqn = descriptor.accept(ExtractSymbolExtensionReceiverType, Unit)?.takeIf { !it.isRoot }

                        if (canStoreFqName(descriptorFqn) && (extensionReceiverFqn?.let { canStoreFqName(it) } ?: true)) {
                            for (fqn in listOf(descriptorFqn, extensionReceiverFqn).filterNotNull()) {
                                FqNames.replace {
                                    it[fqName] = fqn.toString()
                                    it[shortName] = fqn.shortName().toString()
                                }
                            }

                            Symbols.replace {
                                it[fqName] = descriptorFqn.toString()
                                it[kind] = descriptor.accept(ExtractSymbolKind, Unit).rawValue
                                it[visibility] = descriptor.accept(ExtractSymbolVisibility, Unit).rawValue
                                it[extensionReceiverType] = extensionReceiverFqn?.toString()
                            }
                        } else {
                            LOG.warn("Excluding symbol {} from index since its name is too long", descriptorFqn.toString())
                        }
                    }

                    val finished = System.currentTimeMillis()
                    val count = Symbols.slice(Symbols.fqName.count()).selectAll().first()[Symbols.fqName.count()]
                    LOG.info("Updated symbol index in ${finished - started} ms! (${count} symbol(s))")
                }
            } catch (e: Exception) {
                LOG.error("Error while updating symbol index")
                LOG.printStackTrace(e)
            }

            progress.close()
        }
    }

    private fun canStoreFqName(fqName: FqName) =
           fqName.toString().length <= MAX_FQNAME_LENGTH
        && fqName.shortName().toString().length <= MAX_SHORT_NAME_LENGTH

    fun query(prefix: String, receiverType: FqName? = null, limit: Int = 20): List<Symbol> = transaction(db) {
        // TODO: Extension completion currently only works if the receiver matches exactly,
        //       ideally this should work with subtypes as well
        (Symbols innerJoin FqNames)
            .select { FqNames.shortName.like("$prefix%") and (Symbols.extensionReceiverType eq receiverType?.toString()) }
            .limit(limit)
            .map { Symbol(
                fqName = FqName(it[Symbols.fqName]),
                kind = Symbol.Kind.fromRaw(it[Symbols.kind]),
                visibility = Symbol.Visibility.fromRaw(it[Symbols.visibility]),
                extensionReceiverType = it[Symbols.extensionReceiverType]?.let(::FqName)
            ) }
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
