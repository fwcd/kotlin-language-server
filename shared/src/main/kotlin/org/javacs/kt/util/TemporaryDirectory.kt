package org.javacs.kt.util

import java.io.Closeable
import java.nio.file.Path
import java.nio.file.Files

/**
 * A directory in which temporary files may be created.
 * The advantage of using this class over a standard
 * function such as Files.createTempFile is that all
 * temp files in the directory can easily be disposed
 * of once no longer needed.
 */
class TemporaryDirectory(prefix: String = "kotlinlangserver") : Closeable {
    private val dirPath: Path = Files.createTempDirectory(prefix)

    fun createTempFile(prefix: String = "tmp", suffix: String = ""): Path = Files.createTempFile(dirPath, prefix, suffix)

    override fun close() {
        if (Files.exists(dirPath)) {
            dirPath.toFile().deleteRecursively()
        }
    }
}
