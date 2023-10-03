package org.javacs.kt.database

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
        const val CURRENT_VERSION = 1
        const val DB_FILENAME = "kls_database.db"
    }

    var db: Database? = null
        private set

    fun setup(storagePath: Path?) {
        db = getDbFromFile(storagePath)

        val currentVersion = transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(DatabaseMetadata)

            DatabaseMetadataEntity.all().firstOrNull()?.version
        }

        if ((currentVersion ?: 0) < CURRENT_VERSION) {
            deleteDb(storagePath)

            db = getDbFromFile(storagePath)

            transaction(db) {
                SchemaUtils.createMissingTablesAndColumns(DatabaseMetadata)

                DatabaseMetadata.deleteAll()
                DatabaseMetadata.insert { it[version] = CURRENT_VERSION }
            }
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
