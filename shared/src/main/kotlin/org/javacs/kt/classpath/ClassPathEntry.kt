package org.javacs.kt.classpath

import kotlinx.serialization.Serializable
import org.javacs.kt.storage.PathAsStringSerializer
import java.nio.file.Path

@Serializable
data class ClassPathEntry(
    @Serializable(with = PathAsStringSerializer::class)
    val compiledJar: Path,
    @Serializable(with = PathAsStringSerializer::class)
    val sourceJar: Path? = null
)
