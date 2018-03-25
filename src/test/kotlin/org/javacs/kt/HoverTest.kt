package org.javacs.kt

import com.intellij.openapi.Disposable
import com.intellij.psi.util.PsiTreeUtil
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasToString
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.junit.Assert.assertThat
import org.junit.Test

class HoverTest {
    private val config = CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MODULE_NAME, "test-module")
    }
    private val env = KotlinCoreEnvironment.createForProduction(
            parentDisposable = Disposable { },
            configuration = config,
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES)
    private val parser = KtPsiFactory(env.project)

    private data class ParseAnalyzeResult(val file: KtFile, val analyze: AnalysisResult)

    private fun parseAnalyze(text: String): ParseAnalyzeResult {
        val file = parser.createFile("HelloWorld.kt", text)
        val analyze = TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                project = env.project,
                files = listOf(file),
                trace = CliBindingTrace(),
                configuration = env.configuration,
                packagePartProvider = env::createPackagePartProvider)
        return ParseAnalyzeResult(file, analyze)
    }

    private fun findExpressionAt(file: KtFile, offset: Int): KtExpression? {
        return PsiTreeUtil.getParentOfType(file.findElementAt(offset), KtExpression::class.java)
    }

    private fun parent(ex: KtExpression?): KtExpression? {
        return PsiTreeUtil.getParentOfType(ex, KtExpression::class.java)
    }

    @Test
    fun `run the Kotlin compiler`() {
        val text = """
fun main(args: Array<String>) {
    println("Hello world!")
}"""
        val (file, _) = parseAnalyze(text)
        val ex = findExpressionAt(file, 40)

        assertThat(ex?.text, equalTo("println"))
        assertThat(parent(ex)?.text, equalTo("""println("Hello world!")"""))
    }

    @Test
    fun `find the types of expressions`() {
        val text = """
fun main(): string {
    val text = ""
    return text
}"""
        val (file, analyze) = parseAnalyze(text)
        val stringLiteral = findExpressionAt(file, 38)!!
        val textDeclaration = findExpressionAt(file, 32)!!
        val textReference = findExpressionAt(file, 53)!!

        assertThat(stringLiteral.text, equalTo("\"\""))
        assertThat(textDeclaration.text, equalTo("""val text = """""))
        assertThat(textReference.text, equalTo("text"))

        val stringLiteralType = analyze.bindingContext.getType(stringLiteral)
        val textDeclarationType = analyze.bindingContext.getType(textDeclaration)
        val textReferenceType = analyze.bindingContext.getType(textReference)

        assertThat(stringLiteralType, hasToString("String"))
        assertThat(textDeclarationType, hasToString("Unit"))
        assertThat(textReferenceType, hasToString("String"))
    }
}