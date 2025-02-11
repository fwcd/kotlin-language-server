package org.javacs.kt

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test

class SourceExclusionsTest {
    private val workspaceRoot =
        File(System.getProperty("java.io.tmpdir"), "test-workspace").toPath()
    private lateinit var sourceExclusions: SourceExclusions

    @Before
    fun setup() {
        Files.createDirectories(workspaceRoot)

        val gitignore = workspaceRoot.resolve(".gitignore")
        Files.write(
            gitignore,
            listOf("*.log", "output/", "temp/", "# comment to ignore", "build-output/", "*.tmp"),
        )

        sourceExclusions =
            SourceExclusions(
                workspaceRoots = listOf(workspaceRoot),
                scriptsConfig = ScriptsConfiguration(enabled = true, buildScriptsEnabled = true),
            )
    }

    @After
    fun cleanup() {
        workspaceRoot.toFile().deleteRecursively()
    }

    @Test
    fun `test path exclusions`() {
        assertThat(sourceExclusions.isPathIncluded(workspaceRoot.resolve(".git")), equalTo(false))
        assertThat(
            sourceExclusions.isPathIncluded(workspaceRoot.resolve("node_modules")),
            equalTo(false),
        )
        assertThat(sourceExclusions.isPathIncluded(workspaceRoot.resolve(".idea")), equalTo(false))

        assertThat(
            sourceExclusions.isPathIncluded(
                workspaceRoot.resolve("src").resolve("main").resolve("kotlin")
            ),
            equalTo(true),
        )
        assertThat(
            sourceExclusions.isPathIncluded(
                workspaceRoot.resolve("src").resolve("test").resolve("kotlin")
            ),
            equalTo(true),
        )
    }

    @Test
    fun `test gitignore patterns`() {
        assertThat(
            sourceExclusions.isPathIncluded(workspaceRoot.resolve("debug.log")),
            equalTo(false),
        )
        assertThat(
            sourceExclusions.isPathIncluded(workspaceRoot.resolve("output").resolve("file.txt")),
            equalTo(false),
        )
        assertThat(sourceExclusions.isPathIncluded(workspaceRoot.resolve("temp")), equalTo(false))
        assertThat(
            sourceExclusions.isPathIncluded(workspaceRoot.resolve("build-output")),
            equalTo(false),
        )
        assertThat(
            sourceExclusions.isPathIncluded(workspaceRoot.resolve("data.tmp")),
            equalTo(false),
        )

        assertThat(
            sourceExclusions.isPathIncluded(workspaceRoot.resolve("src").resolve("main.kt")),
            equalTo(true),
        )
        assertThat(
            sourceExclusions.isPathIncluded(workspaceRoot.resolve("README.md")),
            equalTo(true),
        )
    }

    @Test
    fun `test target directory handling`() {
        assertThat(sourceExclusions.isPathIncluded(workspaceRoot.resolve("target")), equalTo(true))
        assertThat(
            sourceExclusions.isPathIncluded(
                workspaceRoot.resolve("target").resolve("generated-sources")
            ),
            equalTo(true),
        )
        assertThat(
            sourceExclusions.isPathIncluded(workspaceRoot.resolve("target").resolve("classes")),
            equalTo(false),
        )
    }

    @Test
    fun `test URI inclusion`() {
        val includedUri =
            workspaceRoot
                .resolve("src")
                .resolve("main")
                .resolve("kotlin")
                .resolve("Example.kt")
                .toUri()
        val excludedUri = workspaceRoot.resolve(".git").resolve("config").toUri()
        val gitignoreExcludedUri = workspaceRoot.resolve("output").resolve("test.txt").toUri()

        assertThat(sourceExclusions.isURIIncluded(includedUri), equalTo(true))
        assertThat(sourceExclusions.isURIIncluded(excludedUri), equalTo(false))
        assertThat(sourceExclusions.isURIIncluded(gitignoreExcludedUri), equalTo(false))
    }

    @Test
    fun `test paths outside workspace root`() {
        val outsidePath =
            File(System.getProperty("java.io.tmpdir"), "outside-workspace")
                .toPath()
                .resolve("file.kt")
        assertThat(sourceExclusions.isPathIncluded(outsidePath), equalTo(false))
    }

    @Test
    fun `test script handling based on configuration`() {
        val restrictedScriptsExclusions =
            SourceExclusions(
                workspaceRoots = listOf(workspaceRoot),
                scriptsConfig = ScriptsConfiguration(enabled = false),
            )

        assertThat(
            restrictedScriptsExclusions.isPathIncluded(workspaceRoot.resolve("build.gradle.kts")),
            equalTo(false),
        )

        assertThat(
            sourceExclusions.isPathIncluded(workspaceRoot.resolve("build.gradle.kts")),
            equalTo(true),
        )
    }

    @Test
    fun `test gitignore handling with IO errors`() {
        val ioErrorWorkspace = workspaceRoot.resolve("io-error-workspace")
        Files.createDirectories(ioErrorWorkspace)

        val problematicGitignore = ioErrorWorkspace.resolve(".gitignore")
        Files.write(problematicGitignore, listOf("test-pattern"))

        try {
            // Make the file unreadable to simulate IO error
            Files.setPosixFilePermissions(
                problematicGitignore,
                PosixFilePermissions.fromString("--x------"),
            )

            val exclusionsWithIOError =
                SourceExclusions(
                    workspaceRoots = listOf(ioErrorWorkspace),
                    scriptsConfig = ScriptsConfiguration(enabled = true, buildScriptsEnabled = true),
                )

            assertThat(
                exclusionsWithIOError.isPathIncluded(ioErrorWorkspace.resolve(".git")),
                equalTo(false),
            )
            assertThat(
                exclusionsWithIOError.isPathIncluded(
                    ioErrorWorkspace.resolve("src/main/kotlin/Test.kt")
                ),
                equalTo(true),
            )
        } catch (e: UnsupportedOperationException) {
            // Skip test if POSIX permissions are not supported
        }
    }

    @Test
    fun `test multiple gitignore files`() {
        val subdir = workspaceRoot.resolve("subproject")
        Files.createDirectories(subdir)
        Files.write(subdir.resolve(".gitignore"), listOf("subproject-specific.log", "local-temp/"))

        val multiRootExclusions =
            SourceExclusions(
                workspaceRoots = listOf(workspaceRoot, subdir),
                scriptsConfig = ScriptsConfiguration(enabled = true, buildScriptsEnabled = true),
            )

        assertThat(
            multiRootExclusions.isPathIncluded(workspaceRoot.resolve("debug.log")),
            equalTo(false),
        )
        assertThat(
            multiRootExclusions.isPathIncluded(subdir.resolve("subproject-specific.log")),
            equalTo(false),
        )
        assertThat(multiRootExclusions.isPathIncluded(subdir.resolve("local-temp")), equalTo(false))
    }

    @Test
    fun `test empty gitignore handling`() {
        val emptyGitignoreWorkspace = workspaceRoot.resolve("empty-gitignore-workspace")
        Files.createDirectories(emptyGitignoreWorkspace)
        Files.write(emptyGitignoreWorkspace.resolve(".gitignore"), listOf<String>())

        val exclusionsWithEmptyGitignore =
            SourceExclusions(
                workspaceRoots = listOf(emptyGitignoreWorkspace),
                scriptsConfig = ScriptsConfiguration(enabled = true, buildScriptsEnabled = true),
            )

        assertThat(
            exclusionsWithEmptyGitignore.isPathIncluded(emptyGitignoreWorkspace.resolve(".git")),
            equalTo(false),
        )
        assertThat(
            exclusionsWithEmptyGitignore.isPathIncluded(
                emptyGitignoreWorkspace.resolve("src/main/kotlin/Test.kt")
            ),
            equalTo(true),
        )
    }

    @Test
    fun `test non-existent gitignore handling`() {
        val noGitignoreWorkspace = workspaceRoot.resolve("no-gitignore-workspace")
        Files.createDirectories(noGitignoreWorkspace)

        val exclusionsWithoutGitignore =
            SourceExclusions(
                workspaceRoots = listOf(noGitignoreWorkspace),
                scriptsConfig = ScriptsConfiguration(enabled = true, buildScriptsEnabled = true),
            )

        assertThat(
            exclusionsWithoutGitignore.isPathIncluded(noGitignoreWorkspace.resolve(".git")),
            equalTo(false),
        )
        assertThat(
            exclusionsWithoutGitignore.isPathIncluded(
                noGitignoreWorkspace.resolve("src/main/kotlin/Test.kt")
            ),
            equalTo(true),
        )
    }
}
