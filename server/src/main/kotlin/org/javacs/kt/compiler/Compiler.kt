@file:OptIn(ExperimentalCompilerApi::class)
@file:Suppress("DEPRECATION")

package org.javacs.kt.compiler

import com.intellij.lang.Language
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration as KotlinCompilerConfiguration
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.components.InferenceSession
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.definitions.CliScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition // Legacy
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.util.KotlinFrontEndException
import java.io.Closeable
import java.nio.file.Path
import java.nio.file.Paths
import java.net.URLClassLoader
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.DependenciesResolver.ResolveResult
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.jvm.JvmDependency
import org.javacs.kt.LOG
import org.javacs.kt.CompilerConfiguration
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.util.LoggingMessageCollector
import org.jetbrains.kotlin.cli.common.output.writeAllTo
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.samWithReceiver.CliSamWithReceiverComponentContributor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import java.io.File

private val GRADLE_DSL_DEPENDENCY_PATTERN = Regex("^gradle-(?:kotlin-dsl|core).*\\.jar$")

/**
 * Kotlin compiler APIs used to parse, analyze and compile
 * files and expressions.
 */
class CompilationEnvironment(
    javaSourcePath: Set<Path>,
    classPath: Set<Path>
) : Closeable {
    private val disposable = Disposer.newDisposable()

    val environment: KotlinCoreEnvironment
    val parser: KtPsiFactory
    val scripts: ScriptDefinitionProvider

    init {
        environment = KotlinCoreEnvironment.createForProduction(
            parentDisposable = disposable,
            // Not to be confused with the CompilerConfiguration in the language server Configuration
            configuration = KotlinCompilerConfiguration().apply {
                val langFeatures = mutableMapOf<LanguageFeature, LanguageFeature.State>()
                for (langFeature in LanguageFeature.values()) {
                    langFeatures[langFeature] = LanguageFeature.State.ENABLED
                }
                val languageVersionSettings = LanguageVersionSettingsImpl(
                    LanguageVersion.LATEST_STABLE,
                    ApiVersion.createByLanguageVersion(LanguageVersion.LATEST_STABLE),
                    emptyMap(),
                    langFeatures
                )

                put(CommonConfigurationKeys.MODULE_NAME, JvmProtoBufUtil.DEFAULT_MODULE_NAME)
                put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, languageVersionSettings)
                put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, LoggingMessageCollector)
                add(ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS, ScriptingCompilerConfigurationComponentRegistrar())
                put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)

                // configure jvm runtime classpaths
                configureJdkClasspathRoots()

                addJvmClasspathRoots(classPath.map { it.toFile() })
                addJavaSourceRoots(javaSourcePath.map { it.toFile() })

                // Setup script templates (e.g. used by Gradle's Kotlin DSL)
                val scriptDefinitions: MutableList<ScriptDefinition> = mutableListOf(ScriptDefinition.getDefault(defaultJvmScriptingHostConfiguration))

                if (classPath.any { GRADLE_DSL_DEPENDENCY_PATTERN.matches(it.fileName.toString()) }) {
                    LOG.info("Configuring Kotlin DSL script templates...")

                    val scriptTemplates = listOf(
                        "org.gradle.kotlin.dsl.KotlinInitScript",
                        "org.gradle.kotlin.dsl.KotlinSettingsScript",
                        "org.gradle.kotlin.dsl.KotlinBuildScript"
                    )

                    try {
                        // Load template classes
                        val scriptClassLoader = URLClassLoader(classPath.map { it.toUri().toURL() }.toTypedArray())
                        val fileClassPath = classPath.map { it.toFile() }
                        val scriptHostConfig = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
                            configurationDependencies(JvmDependency(fileClassPath))
                        }
                        // TODO: KotlinScriptDefinition will soon be deprecated, use
                        //       ScriptDefinition.compilationConfiguration and its defaultImports instead
                        //       of KotlinScriptDefinition.dependencyResolver
                        // TODO: Use ScriptDefinition.FromLegacyTemplate directly if possible
                        // scriptDefinitions = scriptTemplates.map { ScriptDefinition.FromLegacyTemplate(scriptHostConfig, scriptClassLoader.loadClass(it).kotlin) }
                        scriptDefinitions.addAll(scriptTemplates.map { ScriptDefinition.FromLegacy(scriptHostConfig, object : KotlinScriptDefinitionFromAnnotatedTemplate(
                            scriptClassLoader.loadClass(it).kotlin,
                            scriptHostConfig[ScriptingHostConfiguration.getEnvironment]?.invoke()
                        ) {
                            override fun isScript(fileName: String): Boolean {
                                // The pattern for KotlinSettingsScript doesn't seem to work well, so kinda "forcing it" for settings.gradle.kts files
                                if (this.template.simpleName == "KotlinSettingsScript" && fileName.endsWith("settings.gradle.kts")) {
                                    return true
                                }

                                return super.isScript(fileName)
                            }

                            override val dependencyResolver: DependenciesResolver = object : DependenciesResolver {
                                override fun resolve(scriptContents: ScriptContents, environment: Environment) = ResolveResult.Success(ScriptDependencies(
                                    imports = BuildFileManager.defaultModel.implicitImports
                                ))
                            }
                        }) })
                    } catch (e: Exception) {
                        LOG.error("Error while loading script template classes")
                        LOG.printStackTrace(e)
                    }
                }

                LOG.info("Adding script definitions ${scriptDefinitions.map { it.asLegacyOrNull<KotlinScriptDefinition>()?.template?.simpleName }}")
                addAll(ScriptingConfigurationKeys.SCRIPT_DEFINITIONS, scriptDefinitions)
            },
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        // hacky way to support SamWithReceiverAnnotations for scripts
        val scriptDefinitions: List<ScriptDefinition> = environment.configuration.getList(ScriptingConfigurationKeys.SCRIPT_DEFINITIONS)
        scriptDefinitions.takeIf { it.isNotEmpty() }?.let {
            val annotations = scriptDefinitions.flatMap { it.asLegacyOrNull<KotlinScriptDefinition>()?.annotationsForSamWithReceivers ?: emptyList() }
            StorageComponentContainerContributor.registerExtension(environment.project, CliSamWithReceiverComponentContributor(annotations))
        }
        val project = environment.project
        parser = KtPsiFactory(project)
        scripts = ScriptDefinitionProvider.getInstance(project)!! as CliScriptDefinitionProvider
    }

    fun updateConfiguration(config: CompilerConfiguration) {
        JvmTarget.fromString(config.jvm.target)
            ?.let { environment.configuration.put(JVMConfigurationKeys.JVM_TARGET, it) }
    }

    fun createContainer(sourcePath: Collection<KtFile>): Pair<ComponentProvider, BindingTraceContext> {
        val trace = CliBindingTrace()
        val container = TopDownAnalyzerFacadeForJVM.createContainer(
            project = environment.project,
            files = sourcePath,
            trace = trace,
            configuration = environment.configuration,
            packagePartProvider = environment::createPackagePartProvider,
            // TODO FileBasedDeclarationProviderFactory keeps indices, re-use it across calls
            declarationProviderFactory = ::FileBasedDeclarationProviderFactory
        )
        return Pair(container, trace)
    }

    override fun close() {
        Disposer.dispose(disposable)
    }
}

/**
 * Determines the compilation environment used
 * by the compiler (and thus the class path).
 */
enum class CompilationKind {
    /** Uses the default class path. */
    DEFAULT,
    /** Uses the Kotlin DSL class path if available. */
    BUILD_SCRIPT
}

/**
 * Incrementally compiles files and expressions.
 * The basic strategy for compiling one file at-a-time is outlined in OneFilePerformance.
 */
class Compiler(javaSourcePath: Set<Path>, classPath: Set<Path>, buildScriptClassPath: Set<Path> = emptySet(), private val outputDirectory: File) : Closeable {
    private var closed = false
    private val localFileSystem: VirtualFileSystem

    private val defaultCompileEnvironment = CompilationEnvironment(javaSourcePath, classPath)
    private val buildScriptCompileEnvironment = buildScriptClassPath.takeIf { it.isNotEmpty() }?.let { CompilationEnvironment(emptySet(), it) }
    private val compileLock = ReentrantLock() // TODO: Lock at file-level

    companion object {
        init {
            setIdeaIoUseFallback()
        }
    }

    init {
        localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
    }

    private fun getBuildEnvByFile(name: String) : CompilationEnvironment {
        val path = Path.of(name)
        // the second statement is needed because if script has error then we shouldn't invoke TAPI again - we just give to it common environment
        // when build conf don't contain error, compilation environment will be updated here
        // TODO: here there is race
        if (BuildFileManager.buildEnvByFile[path] == null && !BuildFileManager.buildConfContainsError()){
            LOG.info { "null build environment for $path" }
            // at the first time TAPI will be invoked for all projects
            if (BuildFileManager.buildEnvByFile.isEmpty()){
                LOG.info { "update build environments for all" }
                BuildFileManager.updateBuildEnvironments()
            }
            else{
                LOG.info { "update build environments for concrete workspace" }
                BuildFileManager.updateBuildEnvironment(path)
            }
        }
        return BuildFileManager.buildEnvByFile[path] ?: buildScriptCompileEnvironment !!
    }

    /**
     * Updates the compiler environment using the given
     * configuration (which is a class from this project).
     */
    fun updateConfiguration(config: CompilerConfiguration) {
        defaultCompileEnvironment.updateConfiguration(config)
        buildScriptCompileEnvironment?.updateConfiguration(config)
    }

    fun createPsiFile(content: String, file: Path = Paths.get("dummy.virtual.kt"), language: Language = KotlinLanguage.INSTANCE, kind: CompilationKind = CompilationKind.DEFAULT): PsiFile {
        assert(!content.contains('\r'))

        val new = psiFileFactoryFor(kind).createFileFromText(file.toString(), language, content, true, false)
        assert(new.virtualFile != null)

        return new
    }

    fun createKtFile(content: String, file: Path = Paths.get("dummy.virtual.kt"), kind: CompilationKind = CompilationKind.DEFAULT): KtFile =
            createPsiFile(content, file, language = KotlinLanguage.INSTANCE, kind = kind) as KtFile

    fun createKtExpression(content: String, file: Path = Paths.get("dummy.virtual.kt"), kind: CompilationKind = CompilationKind.DEFAULT): KtExpression {
        val property = createKtDeclaration("val x = $content", file, kind) as KtProperty
        return property.initializer!!
    }

    fun createKtDeclaration(content: String, file: Path = Paths.get("dummy.virtual.kt"), kind: CompilationKind = CompilationKind.DEFAULT): KtDeclaration {
        val parse = createKtFile(content, file, kind)
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

    private fun compileEnvironmentFor(kind: CompilationKind): CompilationEnvironment = when (kind) {
        CompilationKind.DEFAULT -> defaultCompileEnvironment
        CompilationKind.BUILD_SCRIPT -> buildScriptCompileEnvironment ?: defaultCompileEnvironment
    }

    fun psiFileFactoryFor(kind: CompilationKind): PsiFileFactory =
        PsiFileFactory.getInstance(compileEnvironmentFor(kind).environment.project)

    fun compileKtFile(file: KtFile, sourcePath: Collection<KtFile>, kind: CompilationKind = CompilationKind.DEFAULT): Pair<BindingContext, ModuleDescriptor> =
        compileKtFiles(listOf(file), sourcePath, kind)

    fun compileKtFiles(files: Collection<KtFile>, sourcePath: Collection<KtFile>, kind: CompilationKind = CompilationKind.DEFAULT): Pair<BindingContext, ModuleDescriptor> {
        if (kind == CompilationKind.BUILD_SCRIPT) {
            // Print the (legacy) script template used by the compiled Kotlin DSL build file
            files.forEach { LOG.debug { "$it -> ScriptDefinition: ${it.findScriptDefinition()?.asLegacyOrNull<KotlinScriptDefinition>()?.template?.simpleName}" } }
        }

        compileLock.withLock {
            var compileEnv = compileEnvironmentFor(kind)
            LOG.warn { "compiling $files with $kind" }
            if (files.size == 1 && kind == CompilationKind.BUILD_SCRIPT){
                val nameOfFile = files.first().name
                compileEnv = getBuildEnvByFile(nameOfFile)
            }
            val (container, trace) = compileEnv.createContainer(sourcePath)
            val module = container.getService(ModuleDescriptor::class.java)
            container.get<LazyTopDownAnalyzer>().analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files)
            return Pair(trace.bindingContext, module)
        }
    }

    fun compileKtExpression(expression: KtExpression, scopeWithImports: LexicalScope, sourcePath: Collection<KtFile>, kind: CompilationKind = CompilationKind.DEFAULT): Pair<BindingContext, ComponentProvider> {
        try {
            // Use same lock as 'compileFile' to avoid concurrency issues such as #42
            compileLock.withLock {
                val compileEnv = compileEnvironmentFor(kind)
                val (container, trace) = compileEnv.createContainer(sourcePath)
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
            }
        } catch (e: KotlinFrontEndException) {
            throw KotlinLSException("Error while analyzing: ${describeExpression(expression.text)}", e)
        }
    }

    fun removeGeneratedCode(files: Collection<KtFile>) {
        files.forEach { file ->
            file.declarations.forEach { declaration ->
                outputDirectory.resolve(
                    file.packageFqName.asString().replace(".", File.separator) + File.separator + declaration.name + ".class"
                ).delete()
            }
        }
    }

    fun generateCode(module: ModuleDescriptor, bindingContext: BindingContext, files: Collection<KtFile>) {
        outputDirectory.let {
            compileLock.withLock {
                val compileEnv = compileEnvironmentFor(CompilationKind.DEFAULT)
                val state = GenerationState.Builder(
                    project = compileEnv.environment.project,
                    builderFactory = ClassBuilderFactories.BINARIES,
                    module = module,
                    bindingContext = bindingContext,
                    files = files.toList(),
                    configuration = compileEnv.environment.configuration
                ).build()
                KotlinCodegenFacade.compileCorrectFiles(state)
                state.factory.writeAllTo(it)
            }
        }
    }

    override fun close() {
        if (!closed) {
            defaultCompileEnvironment.close()
            buildScriptCompileEnvironment?.close()
            closed = true
        } else {
            LOG.warn("Compiler is already closed!")
        }
    }
}

private fun describeExpression(expression: String): String = expression.lines().let { lines ->
    if (lines.size < 5) {
        expression
    } else {
        (lines.take(3) + listOf("...", lines.last())).joinToString(separator = "\n")
    }
}
