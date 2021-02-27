package org.javacs.kt.progress

interface Progress {
    fun update(percent: Int): Unit
}
