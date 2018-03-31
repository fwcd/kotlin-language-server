package org.javacs.kt

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JvmTarget.JVM_1_6
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.getValue
import org.jetbrains.kotlin.container.useImpl
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.AnnotationResolverImpl
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.createContainer
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.scopes.utils.parentsWithSelf
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import java.net.URI

class LiveFile(private val fileName: URI, private var text: String) {
    private var file = parser.createFile(text)
    private var analyze = analyzeFiles(file)
    /** For testing */
    var reAnalyzed = false

    fun hoverAt(newText: String, cursor: Int): Pair<TextRange, DeclarationDescriptor>? {
        val strategy = recover(newText, cursor) ?: return null
        val start = strategy.oldRange.startOffset
        val leaf = strategy.newExpr.findElementAt(cursor - start) ?: return null
        val elements = leaf.parentsWithSelf.filterIsInstance<PsiElement>()
        val stopAtDeclaration = elements.takeWhile { it !is KtDeclaration }
        val expressionsOnly = stopAtDeclaration.filterIsInstance<KtExpression>()
        val hasHover = expressionsOnly.filter { hoverDecl(it, strategy.newContext) != null }
        val expr = hasHover.firstOrNull() ?: return null
        val range = expr.textRange.shiftRight(strategy.oldRange.startOffset)
        val hover = hoverDecl(expr, strategy.newContext) ?: return null

        return Pair(range, hover)
    }

    private fun hoverDecl(expr: KtExpression, context: BindingContext): DeclarationDescriptor? {
        return when (expr) {
            is KtReferenceExpression -> context.get(BindingContext.REFERENCE_TARGET, expr) ?: return null
            else -> null
        }
    }

    fun completionsAt(newText: String, cursor: Int): Sequence<DeclarationDescriptor> {
        val strategy = recover(newText, cursor) ?: return emptySequence()
        val leaf = strategy.newExpr.findElementAt(cursor - strategy.oldRange.startOffset - 1) ?: return emptySequence()
        val elements = leaf.parentsWithSelf.filterIsInstance<KtElement>()
        val exprs = elements.takeWhile { it is KtExpression }.filterIsInstance<KtExpression>()
        val dot = exprs.filterIsInstance<KtDotQualifiedExpression>().firstOrNull()
        if (dot != null) return completeMembers(dot, strategy).asSequence()
        val ref = exprs.filterIsInstance<KtNameReferenceExpression>().firstOrNull()
        if (ref != null) return completeId(ref, strategy)

        return emptySequence()
    }

    private fun completeId(
            ref: KtNameReferenceExpression,
            strategy: RecoveryStrategy): Sequence<DeclarationDescriptor> {
        val scope = findScope(ref, strategy.newContext) ?: return emptySequence()
        val nameFilter = partialId(ref)
        val found = scope.parentsWithSelf.flatMap {
            it.getContributedDescriptors(DescriptorKindFilter.ALL, nameFilter).asSequence()
        }

        return found
    }

    private fun completeMembers(
            dot: KtDotQualifiedExpression,
            strategy: RecoveryStrategy): Collection<DeclarationDescriptor> {
        val type = strategy.newContext.getType(dot.receiverExpression)
                   ?: robustType(dot.receiverExpression, strategy.newContext)
                   ?: return emptyList()
        val nameFilter = partialId(dot.selectorExpression)
        val found = type.memberScope.getDescriptorsFiltered(DescriptorKindFilter.ALL, nameFilter)

        return found
    }

    private fun partialId(exprAtCursor: KtExpression?): (Name) -> Boolean {
        val select = exprAtCursor?.text ?: ""
        val word = Regex("[^()]+")
        val partial = word.find(select)?.value ?: ""

        return {
            containsCharactersInOrder(it.identifier, partial, false)
        }
    }

    /**
     * If we're having trouble figuring out the type of an expression,
     * try re-parsing and re-analyzing just the difficult expression
     */
    fun robustType(expr: KtExpression, context: BindingContext): KotlinType? {
        val scope = findScope(expr, context) ?: return null
        val parse = parser.createExpression(expr.text)
        val analyze = analyzeExpression(parse, scope)

        return analyze.getType(parse)
    }

    private fun findScope(expr: KtExpression, context: BindingContext): LexicalScope? {
        return expr.parentsWithSelf.filterIsInstance<KtElement>().mapNotNull {
            context.get(
                    BindingContext.LEXICAL_SCOPE, it)
        }.firstOrNull()
    }

    fun containsCharactersInOrder(
            candidate: CharSequence, pattern: CharSequence, caseSensitive: Boolean): Boolean {
        var iCandidate = 0
        var iPattern = 0

        while (iCandidate < candidate.length && iPattern < pattern.length) {
            var patternChar = pattern[iPattern]
            var testChar = candidate[iCandidate]

            if (!caseSensitive) {
                patternChar = Character.toLowerCase(patternChar)
                testChar = Character.toLowerCase(testChar)
            }

            if (patternChar == testChar) {
                iPattern++
                iCandidate++
            } else iCandidate++
        }

        return iPattern == pattern.length
    }

    /**
     * Try to re-analyze a section of the file, but fall back on full re-compilation if necessary
     */
    private fun recover(newText: String, cursor: Int): RecoveryStrategy? {
        reAnalyzed = false

        // If there are no changes, we can use the existing analyze
        val changed = changedRegion(newText) ?: return NoChanges()
        // Look for a recoverable expression around the cursor
        val strategy = recoverAt(newText, cursor) ?: return fullRecompile(newText)
        // If the expression that we're going to re-compile doesn't include all the changes, give up
        if (!strategy.willRepair.contains(changed)) return fullRecompile(newText)

        return strategy
    }

    private fun fullRecompile(newText: String): RecoveryStrategy? {
        reAnalyze(newText)
        return NoChanges()
    }

    /**
     * Re-analyze the document if it has changed too much to be analyzed incrementally
     */
    private fun reAnalyze(newText: String) {
        LOG.info("Re-analyzing $fileName")

        text = newText
        file = parser.createFile(newText)
        analyze = analyzeFiles(file)
        reAnalyzed = true
    }

    /**
     * Region that has been changed, in both old-document and new-document coordinates
     */
    private fun changedRegion(newText: String): TextRange? {
        if (text == newText) return null

        val prefix = text.commonPrefixWith(newText).length
        val suffix = text.commonSuffixWith(newText).length

        return TextRange(prefix, text.length - suffix)
    }

    private fun recoverAt(newText: String, cursor: Int): RecoveryStrategy? {
        val leaf = file.findElementAt(cursor) ?: return null
        val elements = leaf.parentsWithSelf.filterIsInstance<KtElement>()
        val hasScope = elements.mapNotNull { recoveryStrategy(newText, it)}
        return hasScope.firstOrNull()
    }

    fun recoveryStrategy(newText: String, element: KtElement): RecoveryStrategy? {
        return when (element) {
            is KtNamedFunction -> {
                val start = element.textRange.startOffset
                val end = element.textRange.endOffset + newText.length - text.length
                val exprText = newText.substring(start, end)
                val expr = parser.createFunction(exprText)
                ReparseFunction(element, expr)
            }
            else -> null
        }
    }

    interface RecoveryStrategy {
        val oldRange: TextRange
        val willRepair: TextRange
        val oldExpr: KtElement
        val newExpr: KtElement
        val newContext: BindingContext
    }

    inner class ReparseFunction(override val oldExpr: KtNamedFunction, override val newExpr: KtNamedFunction): RecoveryStrategy {
        private val oldScope = analyze.bindingContext.get(BindingContext.LEXICAL_SCOPE, oldExpr.bodyExpression)!!
        override val oldRange = oldExpr.textRange
        override val willRepair = oldExpr.bodyExpression!!.textRange
        override val newContext = analyzeExpression(newExpr, oldScope)
    }

    inner class NoChanges: RecoveryStrategy {
        override val oldRange = file.textRange
        override val willRepair = file.textRange
        override val oldExpr = file
        override val newExpr = file
        override val newContext = analyze.bindingContext
    }

    companion object {
        // For non-incremental analyze
        private val config = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)
        }
        private val env = KotlinCoreEnvironment.createForProduction(
                parentDisposable = Disposable { },
                configuration = config,
                configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES)
        private val parser = KtPsiFactory(env.project)

        private fun analyzeFiles(vararg files: KtFile): AnalysisResult =
                TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                        project = env.project,
                        files = files.asList(),
                        trace = CliBindingTrace(),
                        configuration = env.configuration,
                        packagePartProvider = env::createPackagePartProvider)

        // For incremental
        private val module = ModuleDescriptorImpl(
                Name.special("<LanguageServerModule>"),
                LockBasedStorageManager.NO_LOCKS,
                DefaultBuiltIns.Instance).apply {
            setDependencies(listOf(this))
            initialize(PackageFragmentProvider.Empty)
        }
        private val container = createContainer("LanguageServer", JvmPlatform, {
            configureModule(ModuleContext(module, env.project), JvmPlatform, JVM_1_6)
            useInstance(LanguageVersionSettingsImpl.DEFAULT)
            useImpl<AnnotationResolverImpl>()
            useImpl<ExpressionTypingServices>()
        })
        private val expressionTypingServices: ExpressionTypingServices by container

        private fun analyzeExpression(expression: KtExpression, scopeWithImports: LexicalScope): BindingContext {
            val trace = BindingTraceContext()
            expressionTypingServices.getTypeInfo(
                    scopeWithImports, expression, TypeUtils.NO_EXPECTED_TYPE, DataFlowInfo.EMPTY, trace, true)
            return trace.bindingContext
        }
    }
}