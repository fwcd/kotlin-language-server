package org.javacs.kt

import org.javacs.kt.util.LoggingMessageCollector
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.callUtil.getParentResolvedCall
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfoFactory
import org.junit.Test
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.RunnerException
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.io.Closeable
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class OneFilePerformance {
    @State(Scope.Thread)
    class ReusableParts : Closeable {
        internal var config = CompilerConfiguration()
        init {
            config.put(CommonConfigurationKeys.MODULE_NAME, JvmProtoBufUtil.DEFAULT_MODULE_NAME)
            config.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, LoggingMessageCollector)
        }
        internal val disposable = Disposer.newDisposable()
        internal var env = KotlinCoreEnvironment.createForProduction(
            disposable, config, EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        internal var parser = KtPsiFactory(env.project)
        internal var fileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
        internal var bigFile = openFile("/kotlinCompilerPerformance/BigFile.kt")

        internal fun openFile(resourcePath: String?): KtFile {
            val locate = OneFilePerformance::class.java.getResource(resourcePath)
            val file = fileSystem.findFileByPath(URLDecoder.decode(locate.path, StandardCharsets.UTF_8.toString()))
            return PsiManager.getInstance(env.project).findFile(file!!) as KtFile
        }

        internal fun compile(compile: Collection<KtFile>, sourcePath: Collection<KtFile>): BindingTraceContext {
            val trace = CliBindingTrace()
            val container = CompilerFixtures.createContainer(
                env.project,
                sourcePath,
                trace,
                env.configuration,
                env::createPackagePartProvider
            )
            val analyze = container.create(LazyTopDownAnalyzer::class.java)
            analyze.analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, compile, DataFlowInfoFactory.EMPTY)
            return trace
        }

        @TearDown(Level.Trial)
        override fun close() {
            Disposer.dispose(disposable)
        }
    }

    @Benchmark
    fun recompileBigFile(state: ReusableParts) {
        val path = listOf(state.bigFile)
        state.compile(path, path)
    }

    @Benchmark
    fun recompileSmallFile(state: ReusableParts) {
        val smallFile = state.openFile("/kotlinCompilerPerformance/ReferencesBigFile.kt")
        val analyze = state.compile(listOf(smallFile), listOf(smallFile, state.bigFile))
        // Check reference
        val ref = PsiTreeUtil.getNonStrictParentOfType(smallFile.findElementAt(80), KtElement::class.java)
        val call = ref.getParentResolvedCall(analyze.getBindingContext(), false)
        if (!call!!.getCandidateDescriptor().getName().asString().equals("max"))
            throw RuntimeException("Expected BigFile.max but found " + call)
    }

    @Benchmark
    fun recompileBoth(state: ReusableParts) {
        val bigFile = state.openFile("/kotlinCompilerPerformance/BigFile.kt")
        val smallFile = state.openFile("/kotlinCompilerPerformance/ReferencesBigFile.kt")
        val analyze = state.compile(listOf(smallFile, bigFile), listOf(smallFile, bigFile))
        // Check reference
        val ref = PsiTreeUtil.getNonStrictParentOfType(smallFile.findElementAt(80), KtElement::class.java)
        val call = ref.getParentResolvedCall(analyze.getBindingContext(), false)
        if (!call!!.getCandidateDescriptor().getName().asString().equals("max"))
            throw RuntimeException("Expected BigFile.max but found " + call)
    }

    @Test
    fun checkRecompileBoth() {
        ReusableParts().use { state ->
            recompileBoth(state)
        }
    }

    @Test
    fun checkRecompileOne() {
        ReusableParts().use { state ->
            state.compile(setOf(state.bigFile), setOf(state.bigFile))
            recompileSmallFile(state)
        }
    }

    companion object {
        @Throws(RunnerException::class, InterruptedException::class)
        @JvmStatic fun main(args: Array<String>) {
            val opt = OptionsBuilder().include(OneFilePerformance::class.java.getSimpleName())
                    .forks(1)
                    .threads(1)
                    .warmupIterations(5)
                    .measurementIterations(5)
                    .build()
            Runner(opt).run()
        }
    }
}
