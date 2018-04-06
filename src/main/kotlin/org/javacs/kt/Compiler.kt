package org.javacs.kt

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode.TopLevelDeclarations
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import java.nio.file.Path

/**
 * Incrementally compiles files and expressions.
 * The basic strategy for compiling one file at-a-time is outlined in OneFilePerformance.
 */
object Compiler {
    private val config = CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)
    }
    private val env = KotlinCoreEnvironment.createForProduction(
            parentDisposable = Disposable { },
            configuration = config,
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES)
    val parser = KtPsiFactory(env.project)
    private val localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)

    fun openFile(path: Path): KtFile {
        val absolutePath = path.toAbsolutePath().toString()
        val virtualFile = localFileSystem.findFileByPath(absolutePath)
                          ?: throw RuntimeException("Couldn't find $path")
        return PsiManager.getInstance(env.project).findFile(virtualFile) as KtFile
    }

    fun createFile(file: Path, content: String): KtFile {
        val original = openFile(file)
        return PsiFileFactory.getInstance(env.project).createFileFromText(content, original) as KtFile
    }

    private fun createContainer(sourcePath: Collection<KtFile>): Pair<ComponentProvider, BindingTraceContext> {
        val trace = CliBindingTrace()
        val container = TopDownAnalyzerFacadeForJVM.createContainer(
                project = env.project,
                files = listOf(),
                trace = trace,
                configuration = env.configuration,
                packagePartProvider = env::createPackagePartProvider,
                // TODO FileBasedDeclarationProviderFactory keeps indices, re-use it across calls
                declarationProviderFactory = { storageManager, _ ->  FileBasedDeclarationProviderFactory(storageManager, sourcePath) })
        return Pair(container, trace)
    }

    fun compileFile(file: KtFile, sourcePath: Collection<KtFile>): BindingContext {
        val (container, trace) = createContainer(sourcePath)
        val topDownAnalyzer = container.get<LazyTopDownAnalyzer>()
        val analyze = topDownAnalyzer.analyzeDeclarations(TopLevelDeclarations, listOf(file))

        return trace.bindingContext
    }

    fun compileExpression(expression: KtExpression, scopeWithImports: LexicalScope, sourcePath: Collection<KtFile>): BindingContext {
        val (container, trace) = createContainer(sourcePath)
        val incrementalCompiler = container.get<ExpressionTypingServices>()
        incrementalCompiler.getTypeInfo(
                scopeWithImports,
                expression,
                TypeUtils.NO_EXPECTED_TYPE,
                DataFlowInfo.EMPTY,
                trace,
                true)
        return trace.bindingContext
    }
}