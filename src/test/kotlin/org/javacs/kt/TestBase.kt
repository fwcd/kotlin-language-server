package org.javacs.kt

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

abstract class TestBase {
    val testFileName = "TestFile.kt"
    val config = CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)
    }
    val env = KotlinCoreEnvironment.createForProduction(
            parentDisposable = Disposable { },
            configuration = config,
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES)
    val parser = KtPsiFactory(env.project)

    data class ParseAnalyzeResult(val file: KtFile, val analyze: AnalysisResult)

    fun parseAnalyze(text: String): ParseAnalyzeResult {
        val file = parser.createFile(testFileName, text)
        val analyze = analyze(file)
        return ParseAnalyzeResult(file, analyze)
    }

    fun analyze(vararg files: KtFile): AnalysisResult {
        return TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                project = env.project,
                files = files.asList(),
                trace = CliBindingTrace(),
                configuration = env.configuration,
                packagePartProvider = env::createPackagePartProvider)
    }

    fun findExpressionAt(file: KtFile, offset: Int): KtExpression? {
        return PsiTreeUtil.getParentOfType(file.findElementAt(offset), KtExpression::class.java)
    }

    private val localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)

    fun testResourcesFile(relativePath: String): KtFile {
        val absolutePath = javaClass.getResource(relativePath).path
        val virtualFile = localFileSystem.findFileByPath(absolutePath) ?: throw RuntimeException("$absolutePath not found")
        return PsiManager.getInstance(env.project).findFile(virtualFile) as KtFile
    }
}