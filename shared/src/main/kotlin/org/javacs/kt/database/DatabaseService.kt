package org.javacs.kt.database

import org.javacs.kt.LOG
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Files
import java.nio.file.Path

private object DatabaseMetadata : IntIdTable() {
    var version = integer("version")
}

class DatabaseMetadataEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DatabaseMetadataEntity>(DatabaseMetadata)

    var version by DatabaseMetadata.version
}

class DatabaseService {

    companion object {
        const val DB_VERSION = 4
        const val DB_FILENAME = "kls_database.db"
    }

    var db: Database? = null
        private set

    fun setup(storagePath: Path?) {
        db = getDbFromFile(storagePath)

        val currentVersion = transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(DatabaseMetadata)

            DatabaseMetadataEntity.all().firstOrNull()?.version ?: 0
        }

        if (currentVersion != DB_VERSION) {
            LOG.info("Database has version $currentVersion != $DB_VERSION (the required version), therefore it will be rebuilt...")

            deleteDb(storagePath)
            db = getDbFromFile(storagePath)

            transaction(db) {
                SchemaUtils.createMissingTablesAndColumns(DatabaseMetadata)

                DatabaseMetadata.deleteAll()
                DatabaseMetadata.insert { it[version] = DB_VERSION }
            }
        } else {
            LOG.info("Database has the correct version $currentVersion and will be used as-is")
        }
    }

    private fun getDbFromFile(storagePath: Path?): Database? {
        return storagePath?.let {
            if (Files.isDirectory(it)) {
                Database.connect("jdbc:sqlite:${getDbFilePath(it)}")
            } else {
                null
            }
        }
    }

    private fun deleteDb(storagePath: Path?) {
        storagePath?.let { Files.deleteIfExists(getDbFilePath(it)) }
    }

    private fun getDbFilePath(storagePath: Path) = Path.of(storagePath.toString(), DB_FILENAME)
}
