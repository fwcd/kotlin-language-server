package org.javacs.kt

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiFileFactory
import com.intellij.mock.MockProject
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration as KotlinCompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode.TopLevelDeclarations
import org.jetbrains.kotlin.resolve.calls.components.InferenceSession
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys
import org.jetbrains.kotlin.scripting.compiler.plugin.definitions.CliScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.compiler.plugin.definitions.CliScriptDependenciesProvider
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDependenciesProvider
import org.jetbrains.kotlin.scripting.definitions.StandardScriptDefinition
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.util.KotlinFrontEndException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.KotlinNullableNotNullManager
import org.javacs.kt.util.LoggingMessageCollector

/**
 * Incrementally compiles files and expressions.
 * The basic strategy for compiling one file at-a-time is outlined in OneFilePerformance.
 */
class Compiler(classPath: Set<Path>) {
    val environment: KotlinCoreEnvironment

    private var parser: KtPsiFactory
    private var scripts: ScriptDefinitionProvider
    private val localFileSystem: VirtualFileSystem

    companion object {
        init {
            System.setProperty("idea.io.use.fallback", "true")
        }
    }

    init {
        environment = KotlinCoreEnvironment.createForProduction(
            parentDisposable = Disposable {},
            // Not to be confused with the CompilerConfiguration in the language server Configuration
            configuration = KotlinCompilerConfiguration().apply {
                put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)
                put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, LoggingMessageCollector)
                add(ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS, ScriptingCompilerConfigurationComponentRegistrar())
                add(ScriptingConfigurationKeys.SCRIPT_DEFINITIONS, StandardScriptDefinition)
                addJvmClasspathRoots(classPath.map { it.toFile() })
            },
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        val project = environment.project
        if (project is MockProject) {
            project.registerService(NullableNotNullManager::class.java, KotlinNullableNotNullManager(project))
        }

        parser = KtPsiFactory(environment.project)
        scripts = ScriptDefinitionProvider.getInstance(environment.project)!!
        localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
    }

    /**
     * Updates the compiler environment using the given
     * configuration (which is a class from this project).
     */
    fun updateConfiguration(config: CompilerConfiguration) {
        jvmTargetFrom(config.jvm.target)
            ?.let { environment.configuration.put(JVMConfigurationKeys.JVM_TARGET, it) }
    }

    private fun jvmTargetFrom(target: String): JvmTarget? = when (target) {
        // See https://github.com/JetBrains/kotlin/blob/master/compiler/frontend.java/src/org/jetbrains/kotlin/config/JvmTarget.kt
        "default" -> JvmTarget.DEFAULT
        "1.6" -> JvmTarget.JVM_1_6
        "1.8" -> JvmTarget.JVM_1_8
        // "9" -> JvmTarget.JVM_9
        // "10" -> JvmTarget.JVM_10
        // "11" -> JvmTarget.JVM_11
        // "12" -> JvmTarget.JVM_12
        else -> null
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
        try {
            val (container, trace) = createContainer(sourcePath)
            val incrementalCompiler = container.get<ExpressionTypingServices>()
            incrementalCompiler.getTypeInfo(
                    scopeWithImports,
                    expression,
                    TypeUtils.NO_EXPECTED_TYPE,
                    DataFlowInfo.EMPTY,
                    InferenceSession.default,
                    trace,
                    true)
            return Pair(trace.bindingContext, container)
        } catch (e: KotlinFrontEndException) {
            throw KotlinLSException("Error while analyzing: ${expression.text}", e)
        }
    }
}
