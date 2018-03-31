package org.javacs.kt

import com.intellij.openapi.Disposable
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasToString
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.getValue
import org.jetbrains.kotlin.container.useImpl
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.AnnotationResolverImpl
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.createContainer
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.junit.Test

class TypeCheckTest {
    private val config = CompilerConfiguration().apply {
        put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)
    }
    private val env = KotlinCoreEnvironment.createForProduction(
            parentDisposable = Disposable { },
            configuration = config,
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES)
    private val parser = KtPsiFactory(env.project)
    private val module = ModuleDescriptorImpl(
            Name.special("<LanguageServerModule>"),
            LockBasedStorageManager.NO_LOCKS,
            DefaultBuiltIns.Instance).apply {
        setDependencies(listOf(this))
        initialize(PackageFragmentProvider.Empty)
    }
    private val container = createContainer("LanguageServer", JvmPlatform, {
        configureModule(ModuleContext(module, env.project), JvmPlatform, JvmTarget.JVM_1_6)
        useInstance(LanguageVersionSettingsImpl.DEFAULT)
        useImpl<AnnotationResolverImpl>()
        useImpl<ExpressionTypingServices>()
    })
    private val expressionTypingServices: ExpressionTypingServices by container

    private fun expressionType(expression: KtExpression, scopeWithImports: LexicalScope) =
            expressionTypingServices.getType(
                    scopeWithImports,
                    expression,
                    TypeUtils.NO_EXPECTED_TYPE,
                    DataFlowInfo.EMPTY,
                    DummyTrace)

    private fun statementType(expression: KtExpression, scopeWithImports: LexicalScope): BindingContext {
        val trace = BindingTraceContext()
        expressionTypingServices.getTypeInfo(
                scopeWithImports, expression, TypeUtils.NO_EXPECTED_TYPE, DataFlowInfo.EMPTY, trace, true)
        return trace.bindingContext
    }

    private fun analyzeFiles(vararg files: KtFile): AnalysisResult =
            TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                    project = env.project,
                    files = files.asList(),
                    trace = CliBindingTrace(),
                    configuration = env.configuration,
                    packagePartProvider = env::createPackagePartProvider)

    private fun scopeAt(file: KtFile, cursor: Int, analyze: AnalysisResult): LexicalScope? {
        var element = ktElement(file.findElementAt(cursor))

        while (element != null) {
            val scope = analyze.bindingContext.get(BindingContext.LEXICAL_SCOPE, element)

            if (scope != null)
                return scope

            element = ktElement(element)
        }

        return null
    }

    private fun ktElement(psi: PsiElement?) =
            PsiTreeUtil.getParentOfType(psi, KtElement::class.java)

    private fun ktExpression(psi: PsiElement?) =
            PsiTreeUtil.getParentOfType(psi, KtExpression::class.java)

    @Test
    fun `reference a method argument`() {
        val file = parser.createFile("""
class Foo {
    fun foo(bar: String) {
        println(bar)
    }
}
""")
        val analyze = analyzeFiles(file)
        val scope = scopeAt(file, 57, analyze)!!
        val expr = parser.createExpression("bar")
        val type = expressionType(expr, scope)

        assertThat(type, hasToString("String"))
    }

    @Test
    fun `create a new local variable`() {
        val file = parser.createFile("""
class Foo {
    fun foo(bar: String) {
        println(bar)
    }
}
""")
        val analyze = analyzeFiles(file)
        val scope = scopeAt(file, 57, analyze)!!
        val wrapper = parser.createBlock("""
val foo = bar
foo
""")
        val context = statementType(wrapper, scope)
        val foo = ktExpression(wrapper.findElementAt(15))!!
        val fooType = context.getType(foo)

        assertThat(foo.text, hasToString("foo"))
        assertThat(fooType, hasToString("String"))
    }
}
