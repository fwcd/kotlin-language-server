package org.javacs.kt

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

abstract class TestBase {

    protected val testFileName = "TestFile.kt"
    protected val config = CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MODULE_NAME, "test-module")
    }
    protected val env = KotlinCoreEnvironment.createForProduction(
            parentDisposable = Disposable { },
            configuration = config,
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES)
    protected val parser = KtPsiFactory(env.project)

    protected data class ParseAnalyzeResult(val file: KtFile, val analyze: AnalysisResult)

    protected fun parseAnalyze(text: String): ParseAnalyzeResult {
        val file = parser.createFile(testFileName, text)
        val analyze = TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                project = env.project,
                files = listOf(file),
                trace = CliBindingTrace(),
                configuration = env.configuration,
                packagePartProvider = env::createPackagePartProvider)
        return ParseAnalyzeResult(file, analyze)
    }

    protected fun findExpressionAt(file: KtFile, offset: Int): KtExpression? {
        return PsiTreeUtil.getParentOfType(file.findElementAt(offset), KtExpression::class.java)
    }

    protected fun parent(ex: KtExpression?): KtExpression? {
        return PsiTreeUtil.getParentOfType(ex, KtExpression::class.java)
    }
}