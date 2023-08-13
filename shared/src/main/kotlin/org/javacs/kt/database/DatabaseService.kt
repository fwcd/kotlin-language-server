package org.javacs.kt.database

import org.jetbrains.exposed.sql.Database
import java.nio.file.Files
import java.nio.file.Path

class DatabaseService {

    var db: Database? = null
        private set

    fun setup(storagePath: Path?) {
        val dbName = "kls_database"

        db = storagePath?.let {
            if (Files.isDirectory(it)) {
                Database.connect("jdbc:sqlite:${Path.of(storagePath.toString(), dbName)}.db")
            } else {
                null
            }
        }
    }
}
