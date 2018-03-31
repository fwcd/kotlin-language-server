package org.javacs.kt.compiler

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

private val CONFIG = CompilerConfiguration().apply {
    put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)
}
val ENV = KotlinCoreEnvironment.createForProduction(
        parentDisposable = Disposable { },
        configuration = CONFIG,
        configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES)
val PARSER = KtPsiFactory(ENV.project)

fun compileFully(vararg files: KtFile): AnalysisResult =
        TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                project = ENV.project,
                files = files.asList(),
                trace = CliBindingTrace(),
                configuration = ENV.configuration,
                packagePartProvider = ENV::createPackagePartProvider)