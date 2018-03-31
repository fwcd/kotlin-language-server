package org.javacs.kt.compiler

import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.config.JvmTarget.JVM_1_6
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.getValue
import org.jetbrains.kotlin.container.useImpl
import org.jetbrains.kotlin.container.useInstance
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
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

private val MODULE = ModuleDescriptorImpl(
        Name.special("<LanguageServerModule>"),
        LockBasedStorageManager.NO_LOCKS,
        DefaultBuiltIns.Instance).apply {
    setDependencies(listOf(this))
    initialize(PackageFragmentProvider.Empty)
}
private val CONTAINER = createContainer("LanguageServer", JvmPlatform, {
    configureModule(ModuleContext(MODULE, ENV.project), JvmPlatform, JVM_1_6)
    useInstance(LanguageVersionSettingsImpl.DEFAULT)
    useImpl<AnnotationResolverImpl>()
    useImpl<ExpressionTypingServices>()
})
private val INCREMENTAL_COMPILER: ExpressionTypingServices by CONTAINER

fun compileIncrementally(expression: KtExpression, scopeWithImports: LexicalScope): BindingContext {
    val trace = BindingTraceContext()
    INCREMENTAL_COMPILER.getTypeInfo(
            scopeWithImports, expression, TypeUtils.NO_EXPECTED_TYPE, DataFlowInfo.EMPTY, trace, true)
    return trace.bindingContext
}