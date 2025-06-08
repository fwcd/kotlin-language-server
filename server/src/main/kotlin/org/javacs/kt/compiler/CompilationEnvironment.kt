@file:Suppress("DEPRECATION")

package org.javacs.kt.compiler

import com.intellij.openapi.util.Disposer
import java.io.Closeable
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import org.javacs.kt.CompilerConfiguration
import org.javacs.kt.LOG
import org.javacs.kt.ScriptsConfiguration
import org.javacs.kt.util.LoggingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration as CompilerConfigurationApi
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.samWithReceiver.CliSamWithReceiverComponentContributor
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingK2CompilerPluginRegistrar
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate

/**
 * Kotlin compiler APIs used to parse, analyze and compile
 * files and expressions.
 */
internal class CompilationEnvironment(
    javaSourcePath: Set<Path>,
    classPath: Set<Path>,
    scriptsConfig: ScriptsConfiguration
) : Closeable {
    private val disposable = Disposer.newDisposable()

    @OptIn(ExperimentalCompilerApi::class)
    val environment: KotlinCoreEnvironment = KotlinCoreEnvironment.createForProduction(
        projectDisposable = disposable,
        // Not to be confused with the CompilerConfiguration in the language server Configuration
        configuration = CompilerConfigurationApi().apply {
            val langFeatures = mutableMapOf<LanguageFeature, LanguageFeature.State>()
            for (langFeature in LanguageFeature.entries) {
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
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, LoggingMessageCollector)
            add(ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS, ScriptingCompilerConfigurationComponentRegistrar())
            add(CompilerPluginRegistrar.COMPILER_PLUGIN_REGISTRARS, ScriptingK2CompilerPluginRegistrar())
            put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)

            // configure jvm runtime classpaths
            configureJdkClasspathRoots()

            // Kotlin 1.8.20 requires us to specify the JDK home, otherwise java.* classes won't resolve
            // See https://github.com/JetBrains/kotlin-compiler-server/pull/626
            val jdkHome = File(System.getProperty("java.home"))
            put(JVMConfigurationKeys.JDK_HOME, jdkHome)

            addJvmClasspathRoots(classPath.map { it.toFile() })
            addJavaSourceRoots(javaSourcePath.map { it.toFile() })

            if (scriptsConfig.enabled) {
                // Setup script templates (e.g. used by Gradle's Kotlin DSL)
                val scriptDefinitions: MutableList<ScriptDefinition> =
                    mutableListOf(ScriptDefinition.getDefault(defaultJvmScriptingHostConfiguration))

                val foundDSLDependency = classPath.any { Regex("^gradle-(?:kotlin-dsl|core).*\\.jar$").matches(it.fileName.toString()) }
                if (scriptsConfig.buildScriptsEnabled && foundDSLDependency) {
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
                        scriptDefinitions.addAll(scriptTemplates.map {
                            ScriptDefinition.FromLegacy(
                                scriptHostConfig,
                                object : KotlinScriptDefinitionFromAnnotatedTemplate(
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

                                    override val dependencyResolver: DependenciesResolver =
                                        object : DependenciesResolver {
                                            override fun resolve(
                                                scriptContents: ScriptContents,
                                                environment: Environment
                                            ) = DependenciesResolver.ResolveResult.Success(
                                                ScriptDependencies(
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
                                                )
                                            )
                                        }
                                })
                        })
                    } catch (e: Exception) {
                        LOG.error("Error while loading script template classes")
                        LOG.printStackTrace(e)
                    }
                }

                LOG.info("Adding script definitions ${scriptDefinitions.map { it.asLegacyOrNull<KotlinScriptDefinition>()?.template?.simpleName }}")
                addAll(ScriptingConfigurationKeys.SCRIPT_DEFINITIONS, scriptDefinitions)
            }
        },
        configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
    )
    private val parser: KtPsiFactory

    init {
        // hacky way to support SamWithReceiverAnnotations for scripts
        val scriptDefinitions: List<ScriptDefinition> = environment.configuration.getList(ScriptingConfigurationKeys.SCRIPT_DEFINITIONS)
        scriptDefinitions.takeIf { it.isNotEmpty() }?.let {
            val annotations = scriptDefinitions.flatMap { it.asLegacyOrNull<KotlinScriptDefinition>()?.annotationsForSamWithReceivers ?: emptyList() }
            StorageComponentContainerContributor.registerExtension(environment.project,
                CliSamWithReceiverComponentContributor(annotations)
            )
        }
        val project = environment.project
        parser = KtPsiFactory(project)
    }

    fun updateConfiguration(config: CompilerConfiguration) {
        JvmTarget.fromString(config.jvm.target)
            ?.let { environment.configuration.put(JVMConfigurationKeys.JVM_TARGET, it) }
    }

    fun createContainer(sourcePath: Collection<KtFile>): Pair<ComponentProvider, BindingTraceContext> {
        val trace = CliBindingTrace(environment.project)
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
