package org.javacs.kt

import com.intellij.openapi.Disposable
import com.intellij.psi.util.PsiTreeUtil
import org.hamcrest.Matchers.equalTo
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.Assert.assertThat
import org.junit.Test

class MainTest {
    @Test
    fun `message() should return "Hello World!"`() {
        assertThat(message(), equalTo("Hello world!"))
    }

    @Test
    fun `run the Kotlin compiler`() {
        val config = CompilerConfiguration()
        config.put(CommonConfigurationKeys.MODULE_NAME, "test-module")
        val env = KotlinCoreEnvironment.createForProduction(
                parentDisposable = Disposable { },
                configuration = config,
                configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val text = """
fun main(args: Array<String>) {
    println("Hello world!")
}"""
        val file = KtPsiFactory(env.project).createFile("HelloWorld.kt", text)
        val analyze = TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                env.project, listOf(file), CliBindingTrace(), env.configuration, env::createPackagePartProvider)
        val el = file.findElementAt(40)!!
        val parent = PsiTreeUtil.getParentOfType(el, KtExpression::class.java)
        val grandParent = PsiTreeUtil.getParentOfType(parent, KtExpression::class.java)

        assertThat(parent?.text, equalTo("println"))
        assertThat(grandParent?.text, equalTo("""println("Hello world!")"""))
    }
}