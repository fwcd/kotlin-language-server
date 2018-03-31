package org.javacs.kt;

import org.jetbrains.kotlin.analyzer.AnalysisResult;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFile;
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

import static java.util.stream.Collectors.joining;

public class KotlinCompilerPerformance {

    private static String readFileText() {
        InputStream in = KotlinCompilerPerformance.class.getResourceAsStream("/kotlinCompilerPerformance/BigFile.kt");
        return new BufferedReader(new InputStreamReader(in)).lines().collect(joining("\n"));
    }

    @State(Scope.Thread)
    public static class KotlinEnv extends TestBase {
        String fileText;

        @Setup(Level.Invocation)
        public void read() {
            fileText = readFileText();
        }
    }

    @Benchmark
    public void parse(KotlinEnv state) {
        KtFile file = state.getParser().createFile(state.fileText);
    }

    @State(Scope.Thread)
    public static class PreParsed extends TestBase {
        KtFile file;

        @Setup(Level.Invocation)
        public void parse() {
            file = getParser().createFile(readFileText());
        }
    }

    @Benchmark
    public void findParsedExpression(PreParsed state) {
        KtExpression expr = state.findExpressionAt(state.file, 2873);
    }

    @Benchmark
    public void analyze(PreParsed state) {
        AnalysisResult analyze = state.analyze(state.file);
    }

    @State(Scope.Thread)
    public static class PreAnalyzed extends TestBase {
        KtFile file;
        AnalysisResult analyze;

        @Setup(Level.Invocation)
        public void analyze() {
            file = getParser().createFile(readFileText());
            analyze = analyze(file);
        }
    }

    @Benchmark
    public void findExpressionType(PreAnalyzed state) {
        KtExpression expr = state.findExpressionAt(state.file, 18123);
        KotlinType type = state.analyze.getBindingContext().getType(expr);
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
