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
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import java.io.File

private val GRADLE_DSL_DEPENDENCY_PATTERN = Regex("^gradle-(?:kotlin-dsl|core).*\\.jar$")

/**
 * Kotlin compiler APIs used to parse, analyze and compile
 * files and expressions.
 */
private class CompilationEnvironment(
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
                            override val dependencyResolver: DependenciesResolver = object : DependenciesResolver {
                                override fun resolve(scriptContents: ScriptContents, environment: Environment) = ResolveResult.Success(ScriptDependencies(
                                    imports = listOf(
                                        "org.gradle.kotlin.dsl.*",
                                        "org.gradle.kotlin.dsl.plugins.dsl.*",
                                        "org.gradle.*",
                                        "org.gradle.api.*",
                                        "org.gradle.api.artifacts.*",
                                        "org.gradle.api.artifacts.component.*",
                                        "org.gradle.api.artifacts.dsl.*",
                                        "org.gradle.api.artifacts.ivy.*",
                                        "org.gradle.api.artifacts.maven.*",
                                        "org.gradle.api.artifacts.query.*",
                                        "org.gradle.api.artifacts.repositories.*",
                                        "org.gradle.api.artifacts.result.*",
                                        "org.gradle.api.artifacts.transform.*",
                                        "org.gradle.api.artifacts.type.*",
                                        "org.gradle.api.artifacts.verification.*",
                                        "org.gradle.api.attributes.*",
                                        "org.gradle.api.attributes.java.*",
                                        "org.gradle.api.capabilities.*",
                                        "org.gradle.api.component.*",
                                        "org.gradle.api.credentials.*",
                                        "org.gradle.api.distribution.*",
                                        "org.gradle.api.distribution.plugins.*",
                                        "org.gradle.api.execution.*",
                                        "org.gradle.api.file.*",
                                        "org.gradle.api.initialization.*",
                                        "org.gradle.api.initialization.definition.*",
                                        "org.gradle.api.initialization.dsl.*",
                                        "org.gradle.api.invocation.*",
                                        "org.gradle.api.java.archives.*",
                                        "org.gradle.api.jvm.*",
                                        "org.gradle.api.logging.*",
                                        "org.gradle.api.logging.configuration.*",
                                        "org.gradle.api.model.*",
                                        "org.gradle.api.plugins.*",
                                        "org.gradle.api.plugins.antlr.*",
                                        "org.gradle.api.plugins.quality.*",
                                        "org.gradle.api.plugins.scala.*",
                                        "org.gradle.api.provider.*",
                                        "org.gradle.api.publish.*",
                                        "org.gradle.api.publish.ivy.*",
                                        "org.gradle.api.publish.ivy.plugins.*",
                                        "org.gradle.api.publish.ivy.tasks.*",
                                        "org.gradle.api.publish.maven.*",
                                        "org.gradle.api.publish.maven.plugins.*",
                                        "org.gradle.api.publish.maven.tasks.*",
                                        "org.gradle.api.publish.plugins.*",
                                        "org.gradle.api.publish.tasks.*",
                                        "org.gradle.api.reflect.*",
                                        "org.gradle.api.reporting.*",
                                        "org.gradle.api.reporting.components.*",
                                        "org.gradle.api.reporting.dependencies.*",
                                        "org.gradle.api.reporting.dependents.*",
                                        "org.gradle.api.reporting.model.*",
                                        "org.gradle.api.reporting.plugins.*",
                                        "org.gradle.api.resources.*",
                                        "org.gradle.api.services.*",
                                        "org.gradle.api.specs.*",
                                        "org.gradle.api.tasks.*",
                                        "org.gradle.api.tasks.ant.*",
                                        "org.gradle.api.tasks.application.*",
                                        "org.gradle.api.tasks.bundling.*",
                                        "org.gradle.api.tasks.compile.*",
                                        "org.gradle.api.tasks.diagnostics.*",
                                        "org.gradle.api.tasks.incremental.*",
                                        "org.gradle.api.tasks.javadoc.*",
                                        "org.gradle.api.tasks.options.*",
                                        "org.gradle.api.tasks.scala.*",
                                        "org.gradle.api.tasks.testing.*",
                                        "org.gradle.api.tasks.testing.junit.*",
                                        "org.gradle.api.tasks.testing.junitplatform.*",
                                        "org.gradle.api.tasks.testing.testng.*",
                                        "org.gradle.api.tasks.util.*",
                                        "org.gradle.api.tasks.wrapper.*",
                                        "org.gradle.authentication.*",
                                        "org.gradle.authentication.aws.*",
                                        "org.gradle.authentication.http.*",
                                        "org.gradle.build.event.*",
                                        "org.gradle.buildinit.plugins.*",
                                        "org.gradle.buildinit.tasks.*",
                                        "org.gradle.caching.*",
                                        "org.gradle.caching.configuration.*",
                                        "org.gradle.caching.http.*",
                                        "org.gradle.caching.local.*",
                                        "org.gradle.concurrent.*",
                                        "org.gradle.external.javadoc.*",
                                        "org.gradle.ide.visualstudio.*",
                                        "org.gradle.ide.visualstudio.plugins.*",
                                        "org.gradle.ide.visualstudio.tasks.*",
                                        "org.gradle.ide.xcode.*",
                                        "org.gradle.ide.xcode.plugins.*",
                                        "org.gradle.ide.xcode.tasks.*",
                                        "org.gradle.ivy.*",
                                        "org.gradle.jvm.*",
                                        "org.gradle.jvm.application.scripts.*",
                                        "org.gradle.jvm.application.tasks.*",
                                        "org.gradle.jvm.platform.*",
                                        "org.gradle.jvm.plugins.*",
                                        "org.gradle.jvm.tasks.*",
                                        "org.gradle.jvm.tasks.api.*",
                                        "org.gradle.jvm.test.*",
                                        "org.gradle.jvm.toolchain.*",
                                        "org.gradle.language.*",
                                        "org.gradle.language.assembler.*",
                                        "org.gradle.language.assembler.plugins.*",
                                        "org.gradle.language.assembler.tasks.*",
                                        "org.gradle.language.base.*",
                                        "org.gradle.language.base.artifact.*",
                                        "org.gradle.language.base.compile.*",
                                        "org.gradle.language.base.plugins.*",
                                        "org.gradle.language.base.sources.*",
                                        "org.gradle.language.c.*",
                                        "org.gradle.language.c.plugins.*",
                                        "org.gradle.language.c.tasks.*",
                                        "org.gradle.language.coffeescript.*",
                                        "org.gradle.language.cpp.*",
                                        "org.gradle.language.cpp.plugins.*",
                                        "org.gradle.language.cpp.tasks.*",
                                        "org.gradle.language.java.*",
                                        "org.gradle.language.java.artifact.*",
                                        "org.gradle.language.java.plugins.*",
                                        "org.gradle.language.java.tasks.*",
                                        "org.gradle.language.javascript.*",
                                        "org.gradle.language.jvm.*",
                                        "org.gradle.language.jvm.plugins.*",
                                        "org.gradle.language.jvm.tasks.*",
                                        "org.gradle.language.nativeplatform.*",
                                        "org.gradle.language.nativeplatform.tasks.*",
                                        "org.gradle.language.objectivec.*",
                                        "org.gradle.language.objectivec.plugins.*",
                                        "org.gradle.language.objectivec.tasks.*",
                                        "org.gradle.language.objectivecpp.*",
                                        "org.gradle.language.objectivecpp.plugins.*",
                                        "org.gradle.language.objectivecpp.tasks.*",
                                        "org.gradle.language.plugins.*",
                                        "org.gradle.language.rc.*",
                                        "org.gradle.language.rc.plugins.*",
                                        "org.gradle.language.rc.tasks.*",
                                        "org.gradle.language.routes.*",
                                        "org.gradle.language.scala.*",
                                        "org.gradle.language.scala.plugins.*",
                                        "org.gradle.language.scala.tasks.*",
                                        "org.gradle.language.scala.toolchain.*",
                                        "org.gradle.language.swift.*",
                                        "org.gradle.language.swift.plugins.*",
                                        "org.gradle.language.swift.tasks.*",
                                        "org.gradle.language.twirl.*",
                                        "org.gradle.maven.*",
                                        "org.gradle.model.*",
                                        "org.gradle.nativeplatform.*",
                                        "org.gradle.nativeplatform.platform.*",
                                        "org.gradle.nativeplatform.plugins.*",
                                        "org.gradle.nativeplatform.tasks.*",
                                        "org.gradle.nativeplatform.test.*",
                                        "org.gradle.nativeplatform.test.cpp.*",
                                        "org.gradle.nativeplatform.test.cpp.plugins.*",
                                        "org.gradle.nativeplatform.test.cunit.*",
                                        "org.gradle.nativeplatform.test.cunit.plugins.*",
                                        "org.gradle.nativeplatform.test.cunit.tasks.*",
                                        "org.gradle.nativeplatform.test.googletest.*",
                                        "org.gradle.nativeplatform.test.googletest.plugins.*",
                                        "org.gradle.nativeplatform.test.plugins.*",
                                        "org.gradle.nativeplatform.test.tasks.*",
                                        "org.gradle.nativeplatform.test.xctest.*",
                                        "org.gradle.nativeplatform.test.xctest.plugins.*",
                                        "org.gradle.nativeplatform.test.xctest.tasks.*",
                                        "org.gradle.nativeplatform.toolchain.*",
                                        "org.gradle.nativeplatform.toolchain.plugins.*",
                                        "org.gradle.normalization.*",
                                        "org.gradle.platform.base.*",
                                        "org.gradle.platform.base.binary.*",
                                        "org.gradle.platform.base.component.*",
                                        "org.gradle.platform.base.plugins.*",
                                        "org.gradle.play.*",
                                        "org.gradle.play.distribution.*",
                                        "org.gradle.play.platform.*",
                                        "org.gradle.play.plugins.*",
                                        "org.gradle.play.plugins.ide.*",
                                        "org.gradle.play.tasks.*",
                                        "org.gradle.play.toolchain.*",
                                        "org.gradle.plugin.devel.*",
                                        "org.gradle.plugin.devel.plugins.*",
                                        "org.gradle.plugin.devel.tasks.*",
                                        "org.gradle.plugin.management.*",
                                        "org.gradle.plugin.use.*",
                                        "org.gradle.plugins.ear.*",
                                        "org.gradle.plugins.ear.descriptor.*",
                                        "org.gradle.plugins.ide.*",
                                        "org.gradle.plugins.ide.api.*",
                                        "org.gradle.plugins.ide.eclipse.*",
                                        "org.gradle.plugins.ide.idea.*",
                                        "org.gradle.plugins.javascript.base.*",
                                        "org.gradle.plugins.javascript.coffeescript.*",
                                        "org.gradle.plugins.javascript.envjs.*",
                                        "org.gradle.plugins.javascript.envjs.browser.*",
                                        "org.gradle.plugins.javascript.envjs.http.*",
                                        "org.gradle.plugins.javascript.envjs.http.simple.*",
                                        "org.gradle.plugins.javascript.jshint.*",
                                        "org.gradle.plugins.javascript.rhino.*",
                                        "org.gradle.plugins.signing.*",
                                        "org.gradle.plugins.signing.signatory.*",
                                        "org.gradle.plugins.signing.signatory.pgp.*",
                                        "org.gradle.plugins.signing.type.*",
                                        "org.gradle.plugins.signing.type.pgp.*",
                                        "org.gradle.process.*",
                                        "org.gradle.swiftpm.*",
                                        "org.gradle.swiftpm.plugins.*",
                                        "org.gradle.swiftpm.tasks.*",
                                        "org.gradle.testing.base.*",
                                        "org.gradle.testing.base.plugins.*",
                                        "org.gradle.testing.jacoco.plugins.*",
                                        "org.gradle.testing.jacoco.tasks.*",
                                        "org.gradle.testing.jacoco.tasks.rules.*",
                                        "org.gradle.testkit.runner.*",
                                        "org.gradle.vcs.*",
                                        "org.gradle.vcs.git.*",
                                        "org.gradle.work.*",
                                        "org.gradle.workers.*"
                                    )
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
            val compileEnv = compileEnvironmentFor(kind)
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
