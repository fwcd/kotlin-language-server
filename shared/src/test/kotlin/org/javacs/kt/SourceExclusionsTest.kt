package org.javacs.kt

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class SourceExclusionsTest {
    private val workspaceRoots = listOf(File("/test/workspace1").toPath(), File("/test/workspace2").toPath())
    private var excludePatterns = listOf<String>()

    @Test
    fun `test only default exclusions`() {
        assertIncluded("/test/workspace1/src/main/kotlin/MyClass.kt")
        assertIncluded("/test/workspace1/build/generated/blah.kt")
        assertExcluded("/test/workspace1/.git")
        assertExcluded("/test/workspace1/.git/HEAD")
    }

    @Test
    fun `test configured exclusions`() {
        excludePatterns = listOf("build","junk")
        assertIncluded("/test/workspace1/src/main/kotlin/MyClass.kt")
        assertExcluded("/test/workspace1/build/generated/blah.kt")
        assertExcluded("/test/workspace1/src/main/kotlin/junk/blah.kt")
    }

    @Test
    fun `test configured directory exclusions`() {
        excludePatterns = listOf("build/dist/**")
        assertIncluded("/test/workspace1/build/generated/blah.kt")
        assertIncluded("/test/workspace1/blah/build/dist/blah.kt")
        assertExcluded("/test/workspace1/build/dist/blah.kt")
    }

    @Test
    fun `test configured wildcard directory exclusions`() {
        excludePatterns = listOf("**/build/dist/**", "build/dist/**")
        assertIncluded("/test/workspace1/build/generated/blah.kt")
        assertExcluded("/test/workspace1/blah/build/dist/blah.kt")
        assertExcluded("/test/workspace1/build/dist/blah.kt")
    }

    fun assertExcluded(path: String) {
        val exclusions = SourceExclusions(workspaceRoots, ScriptsConfiguration(enabled = true, buildScriptsEnabled = true), ExclusionsConfiguration(excludePatterns=excludePatterns))
        assertFalse("Expected $path to be excluded by $excludePatterns", exclusions.isPathIncluded(File(path).toPath()))
    }

    fun assertIncluded(path: String) {
        val exclusions = SourceExclusions(workspaceRoots, ScriptsConfiguration(enabled = true, buildScriptsEnabled = true), ExclusionsConfiguration(excludePatterns=excludePatterns))
        assertTrue("Expected $path to be included by $excludePatterns", exclusions.isPathIncluded(File(path).toPath()))
    }
}
