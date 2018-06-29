package org.javacs.kt;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.container.ComponentProvider;
import org.jetbrains.kotlin.container.ValueDescriptor;
import org.jetbrains.kotlin.descriptors.CallableDescriptor;
import org.jetbrains.kotlin.load.java.JvmAbi;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.resolve.BindingTraceContext;
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer;
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode;
import org.jetbrains.kotlin.resolve.calls.callUtil.CallUtilKt;
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall;
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfoFactory;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.reflect.Type;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class OneFilePerformance {
    @State(Scope.Thread)
    public static class ReusableParts {
        CompilerConfiguration config = new CompilerConfiguration();
        {
            config.put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME);
        }
        KotlinCoreEnvironment env = KotlinCoreEnvironment.createForProduction(new Disposable() {
            @Override
            public void dispose() {
                // Do nothing
            }
        }, config, EnvironmentConfigFiles.JVM_CONFIG_FILES);
        KtPsiFactory parser = new KtPsiFactory(env.getProject());
        VirtualFileSystem fileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL);
        KtFile bigFile = openFile("/kotlinCompilerPerformance/BigFile.kt");

        KtFile openFile(String resourcePath) {
            URL locate = OneFilePerformance.class.getResource(resourcePath);
            VirtualFile file = fileSystem.findFileByPath(locate.getPath());
            return (KtFile) PsiManager.getInstance(env.getProject()).findFile(file);
        }

        BindingTraceContext compile(Collection<KtFile> compile, Collection<KtFile> sourcePath) {
            BindingTraceContext trace = new CliBindingTrace();
            new ComponentProvider(){

                @Override
                public ValueDescriptor resolve(Type arg0) {
                    return null;
                }

                @Override
                public <T> T create(Class<T> arg0) {
                    return null;
                }
            };

            // CompilerFixtures is a Kotlin class in this package
            // and thus might generate an error when using a Java linter.
            // It is safe to ignore that error.
            ComponentProvider container = CompilerFixtures.createContainer(
                    env.getProject(),
                    sourcePath,
                    trace,
                    env.getConfiguration(),
                    env::createPackagePartProvider);
            LazyTopDownAnalyzer analyze = container.create(LazyTopDownAnalyzer.class);

            analyze.analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, compile, DataFlowInfoFactory.EMPTY);

            return trace;
        }
    }

    @Benchmark
    public void recompileBigFile(ReusableParts state) {
        state.compile(Collections.singletonList(state.bigFile), Collections.singletonList(state.bigFile));
    }

    @Benchmark
    public void recompileSmallFile(ReusableParts state) {
        KtFile smallFile = state.openFile("/kotlinCompilerPerformance/ReferencesBigFile.kt");
        BindingTraceContext analyze = state.compile(Arrays.asList(smallFile), Arrays.asList(smallFile, state.bigFile));

        // Check reference
        KtElement ref = PsiTreeUtil.getNonStrictParentOfType(smallFile.findElementAt(80), KtElement.class);
        ResolvedCall<? extends CallableDescriptor> call = CallUtilKt.getParentResolvedCall(ref, analyze.getBindingContext(), false);
        if (!call.getCandidateDescriptor().getName().asString().equals("max"))
            throw new RuntimeException("Expected BigFile.max but found " + call);
    }

    @Benchmark
    public void recompileBoth(ReusableParts state) {
        KtFile bigFile = state.openFile("/kotlinCompilerPerformance/BigFile.kt");
        KtFile smallFile = state.openFile("/kotlinCompilerPerformance/ReferencesBigFile.kt");
        BindingTraceContext analyze = state.compile(Arrays.asList(smallFile, bigFile), Arrays.asList(smallFile, bigFile));

        // Check reference
        KtElement ref = PsiTreeUtil.getNonStrictParentOfType(smallFile.findElementAt(80), KtElement.class);
        ResolvedCall<? extends CallableDescriptor> call = CallUtilKt.getParentResolvedCall(ref, analyze.getBindingContext(), false);
        if (!call.getCandidateDescriptor().getName().asString().equals("max"))
            throw new RuntimeException("Expected BigFile.max but found " + call);
    }

    @Test
    public void checkRecompileBoth() {
        ReusableParts state = new ReusableParts();
        recompileBoth(state);
    }

    @Test
    public void checkRecompileOne() {
        ReusableParts state = new ReusableParts();
        state.compile(Collections.singleton(state.bigFile), Collections.singleton(state.bigFile));
        recompileSmallFile(state);
    }

    public static void main(String[] args) throws RunnerException, InterruptedException {
        Options opt = new OptionsBuilder().include(OneFilePerformance.class.getSimpleName())
                .forks(1)
                .threads(1)
                .warmupIterations(5)
                .measurementIterations(5)
                .build();
        new Runner(opt).run();
    }
}
