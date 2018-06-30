package org.javacs.kt

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileFactory
import com.intellij.mock.MockProject
import org.jetbrains.kotlin.cli.common.script.CliScriptDefinitionProvider
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode.TopLevelDeclarations
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.ScriptDefinitionProvider
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.util.KotlinFrontEndException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.KotlinNullableNotNullManager

/**
 * Incrementally compiles files and expressions.
 * The basic strategy for compiling one file at-a-time is outlined in OneFilePerformance.
 */
class Compiler(classPath: Set<Path>) {
    private val config = CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)
        addAll(JVMConfigurationKeys.CONTENT_ROOTS, classPath.map { JvmClasspathRoot(it.toFile()) })
    }
    val environment: KotlinCoreEnvironment

    init {
        System.setProperty("idea.io.use.fallback", "true")
        environment = KotlinCoreEnvironment.createForProduction(
            parentDisposable = Disposable { },
            configuration = config,
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        val project = environment.project
        if (project is MockProject) {
            project.registerService(NullableNotNullManager::class.java, KotlinNullableNotNullManager(project))
        }
    }

    private val parser = KtPsiFactory(environment.project)
    private val localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
    private val scripts = ScriptDefinitionProvider.getInstance(environment.project) as CliScriptDefinitionProvider

    init {
        scripts.setScriptDefinitions(listOf(KotlinScriptDefinition(Any::class)))
    }

    fun createFile(content: String, file: Path = Paths.get("dummy.kt")): KtFile {
        assert(!content.contains('\r'))

        val factory = PsiFileFactory.getInstance(environment.project)
        val new = factory.createFileFromText(file.toString(), KotlinLanguage.INSTANCE, content, true, false) as KtFile

        assert(new.virtualFile != null)

        return new
    }

    fun createExpression(content: String, file: Path = Paths.get("dummy.kt")): KtExpression {
        val property = parseDeclaration("val x = $content", file) as KtProperty

        return property.initializer!!
    }

    fun createDeclaration(content: String, file: Path = Paths.get("dummy.kt")): KtDeclaration =
            parseDeclaration(content, file)

    private fun parseDeclaration(content: String, file: Path): KtDeclaration {
        val parse = createFile(content, file)
        val declarations = parse.declarations

        assert(declarations.size == 1) { "${declarations.size} declarations in $content" }

        val onlyDeclaration = declarations.first()

        if (onlyDeclaration is KtScript) {
            val scriptDeclarations = onlyDeclaration.declarations

            assert(declarations.size == 1) { "${declarations.size} declarations in script in $content" }

            return scriptDeclarations.first()
        }
        else return onlyDeclaration
    }

    fun createContainer(sourcePath: Collection<KtFile>): Pair<ComponentProvider, BindingTraceContext> {
        val trace = CliBindingTrace()
        val container = TopDownAnalyzerFacadeForJVM.createContainer(
                project = environment.project,
                files = listOf(),
                trace = trace,
                configuration = environment.configuration,
                packagePartProvider = environment::createPackagePartProvider,
                // TODO FileBasedDeclarationProviderFactory keeps indices, re-use it across calls
                declarationProviderFactory = { storageManager, _ ->  FileBasedDeclarationProviderFactory(storageManager, sourcePath) })
        return Pair(container, trace)
    }

    fun compileFile(file: KtFile, sourcePath: Collection<KtFile>): Pair<BindingContext, ComponentProvider> =
            compileFiles(listOf(file), sourcePath)

    // TODO lock at file-level
    private val compileLock = ReentrantLock()

    fun compileFiles(files: Collection<KtFile>, sourcePath: Collection<KtFile>): Pair<BindingContext, ComponentProvider> {
        compileLock.withLock {
            val (container, trace) = createContainer(sourcePath)
            val topDownAnalyzer = container.get<LazyTopDownAnalyzer>()
            topDownAnalyzer.analyzeDeclarations(TopLevelDeclarations, files)

            return Pair(trace.bindingContext, container)
        }
    }

    fun compileExpression(expression: KtExpression, scopeWithImports: LexicalScope, sourcePath: Collection<KtFile>): Pair<BindingContext, ComponentProvider> {
        LOG.info("Compiling ${expression.text}")
        try {
            val (container, trace) = createContainer(sourcePath)
            val incrementalCompiler = container.get<ExpressionTypingServices>()
            incrementalCompiler.getTypeInfo(
                    scopeWithImports,
                    expression,
                    TypeUtils.NO_EXPECTED_TYPE,
                    DataFlowInfo.EMPTY,
                    trace,
                    true)
            return Pair(trace.bindingContext, container)
        } catch (e: KotlinFrontEndException) {
            throw KotlinLSException("Error while analyzing: ${expression.text}", e)
        }
    }
}
