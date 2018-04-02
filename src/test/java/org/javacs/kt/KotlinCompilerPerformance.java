package org.javacs.kt;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.types.KotlinType;
import org.junit.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;

import static java.util.stream.Collectors.joining;

public class KotlinCompilerPerformance {

    private static String readFileText() {
        InputStream in = KotlinCompilerPerformance.class.getResourceAsStream("/kotlinCompilerPerformance/BigFile.kt");
        return new BufferedReader(new InputStreamReader(in)).lines().collect(joining("\n"));
    }

    public static abstract class StateBase {
        Compiler compiler = new Compiler(Collections.emptyList());
    }

    @State(Scope.Thread)
    public static class KotlinEnv extends StateBase {
        String fileText;

        @Setup(Level.Invocation)
        public void read() {
            fileText = readFileText();
        }
    }

    @Benchmark
    public void parse(KotlinEnv state) {
        KtFile file = Compiler.Companion.getParser().createFile(state.fileText);
    }

    @State(Scope.Thread)
    public static class PreParsed extends StateBase {
        KtFile file;

        @Setup(Level.Invocation)
        public void parse() {
            file = Compiler.Companion.getParser().createFile(readFileText());
        }
    }

    @Benchmark
    public void findParsedExpression(PreParsed state) {
        PsiElement expr = state.file.findElementAt(2873);
    }

    @Benchmark
    public void analyze(PreParsed state) {
        BindingContext context = state.compiler.compileFully(state.file);
    }

    @State(Scope.Thread)
    public static class PreAnalyzed extends StateBase {
        KtFile file;
        BindingContext context;

        @Setup(Level.Invocation)
        public void analyze() {
            file = Compiler.Companion.getParser().createFile(readFileText());
            context = compiler.compileFully(file);
        }
    }

    @Benchmark
    public void findExpressionType(PreAnalyzed state) {
        PsiElement psi = state.file.findElementAt(18123);
        KtExpression expr = PsiTreeUtil.getNonStrictParentOfType(psi, KtExpression.class);
        KotlinType type = state.context.getType(expr);
    }

    // TODO compare incremental re-analyze with full re-analyze

    @Test
    public void scratch() {
        KotlinEnv state = new KotlinEnv();
        KotlinCompilerPerformance benchmark = new KotlinCompilerPerformance();
        benchmark.parse(state);
        benchmark.parse(state);
    }

    public static void main(String[] args) throws RunnerException, InterruptedException {
        Options opt = new OptionsBuilder().include(KotlinCompilerPerformance.class.getSimpleName())
                .forks(1)
                .threads(1)
                .warmupIterations(5)
                .measurementIterations(5)
                .build();
        new Runner(opt).run();
    }
}
