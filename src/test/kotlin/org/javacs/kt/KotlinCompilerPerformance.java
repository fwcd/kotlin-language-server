package org.javacs.kt;

import org.jetbrains.kotlin.analyzer.AnalysisResult;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtFile;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class KotlinCompilerPerformance {
    @State(Scope.Thread)
    public static class KotlinEnv extends TestBase { }

    @Benchmark
    public void parse(KotlinEnv state) {
        KtFile file = state.testResourcesFile("/kotlinCompilerPerformance/BigFile.kt");
        KtExpression expr = state.findExpressionAt(file, 2873);
    }

    @Benchmark
    public void parseAnalyze(KotlinEnv state) {
        KtFile file = state.testResourcesFile("/kotlinCompilerPerformance/BigFile.kt");
        AnalysisResult analyze = state.analyze(file);
        KtExpression expr = state.findExpressionAt(file, 2873);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(KotlinCompilerPerformance.class.getSimpleName())
                .forks(1)
                .threads(1)
                .warmupIterations(5)
                .measurementIterations(5)
                .build();
        new Runner(opt).run();
    }
}
