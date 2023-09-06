package org.javacs.kt.classpath

data class ClassPathResult(
    val entries: Set<ClassPathEntry>,
    val cacheHit: Boolean = false
)
