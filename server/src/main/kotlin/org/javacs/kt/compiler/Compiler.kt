package org.javacs.kt.compiler

import com.intellij.lang.Language
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.components.InferenceSession
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition // Legacy
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.util.KotlinFrontEndException
import java.io.Closeable
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.javacs.kt.LOG
import org.javacs.kt.CodegenConfiguration
import org.javacs.kt.CompilerConfiguration
import org.javacs.kt.ScriptsConfiguration
import org.javacs.kt.util.LoggingMessageCollector
import org.jetbrains.kotlin.cli.common.output.writeAllTo
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import java.io.File

/**
 * Incrementally compiles files and expressions.
 * The basic strategy for compiling one file at-a-time is outlined in OneFilePerformance.
 */
class Compiler(
    javaSourcePath: Set<Path>,
    classPath: Set<Path>,
    buildScriptClassPath: Set<Path> = emptySet(),
    scriptsConfig: ScriptsConfiguration,
    private val codegenConfig: CodegenConfiguration,
    private val outputDirectory: File,
) : Closeable {
    private var closed = false

    private val defaultCompileEnvironment = CompilationEnvironment(javaSourcePath, classPath, scriptsConfig)
    private val buildScriptCompileEnvironment = buildScriptClassPath
        .takeIf { it.isNotEmpty() && scriptsConfig.enabled && scriptsConfig.buildScriptsEnabled }
        ?.let { CompilationEnvironment(emptySet(), it, scriptsConfig) }
    private val compileLock = ReentrantLock() // TODO: Lock at file-level

    companion object {
        init {
            setIdeaIoUseFallback()
        }
    }

    /**
     * Updates the compiler environment using the given
     * configuration (which is a class from this project).
     */
    fun updateConfiguration(config: CompilerConfiguration) {
        defaultCompileEnvironment.updateConfiguration(config)
        buildScriptCompileEnvironment?.updateConfiguration(config)
    }

    private fun createPsiFile(content: String, file: Path = Paths.get("dummy.virtual.kt"), language: Language = KotlinLanguage.INSTANCE, kind: CompilationType = CompilationType.DEFAULT): PsiFile {
        assert(!content.contains('\r'))

        val new = psiFileFactoryFor(kind).createFileFromText(file.toString(), language, content, true, false)
        assert(new.virtualFile != null)

        return new
    }

    fun createKtFile(content: String, file: Path = Paths.get("dummy.virtual.kt"), kind: CompilationType = CompilationType.DEFAULT): KtFile =
            createPsiFile(content, file, kind = kind) as KtFile

    fun createKtDeclaration(content: String, file: Path = Paths.get("dummy.virtual.kt"), kind: CompilationType = CompilationType.DEFAULT): KtDeclaration {
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

    private fun compileEnvironmentFor(kind: CompilationType): CompilationEnvironment = when (kind) {
        CompilationType.DEFAULT -> defaultCompileEnvironment
        CompilationType.BUILD_SCRIPT -> buildScriptCompileEnvironment ?: defaultCompileEnvironment
    }

    fun psiFileFactoryFor(kind: CompilationType): PsiFileFactory =
        PsiFileFactory.getInstance(compileEnvironmentFor(kind).environment.project)

    fun compileKtFile(file: KtFile, sourcePath: Collection<KtFile>, kind: CompilationType = CompilationType.DEFAULT): Pair<BindingContext, ModuleDescriptor> =
        compileKtFiles(listOf(file), sourcePath, kind)

    fun compileKtFiles(files: Collection<KtFile>, sourcePath: Collection<KtFile>, kind: CompilationType = CompilationType.DEFAULT): Pair<BindingContext, ModuleDescriptor> {
        if (kind == CompilationType.BUILD_SCRIPT) {
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

    fun compileKtExpression(
        expression: KtExpression,
        scopeWithImports: LexicalScope,
        sourcePath: Collection<KtFile>,
        kind: CompilationType = CompilationType.DEFAULT
    ): Pair<BindingContext, ComponentProvider>? {
        return try {
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
                Pair(trace.bindingContext, container)
            }
        } catch (e: KotlinFrontEndException) {
            LOG.error("""
                Error while analyzing expression: ${describeExpression(expression.text)}
                Message: ${e.message}
                Cause: ${e.cause?.message}
                Stack trace: ${e.attachments.joinToString("\n") { it.displayText }}
            """.trimIndent())
            null
        }
    }

    fun removeGeneratedCode(files: Collection<KtFile>) {
        files.forEach { file ->
            file.declarations.forEach { declaration ->
                val deleted = outputDirectory.resolve(
                    file.packageFqName.asString().replace(".", File.separator) + File.separator + declaration.name + ".class"
                ).delete()
                if (!deleted) {
                    LOG.warn("Failed to delete generated class file for $declaration")
                }
            }
        }
    }

    fun generateCode(module: ModuleDescriptor, bindingContext: BindingContext, files: Collection<KtFile>) {
        outputDirectory.takeIf { codegenConfig.enabled }?.let {
            compileLock.withLock {
                val compileEnv = compileEnvironmentFor(CompilationType.DEFAULT)
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
