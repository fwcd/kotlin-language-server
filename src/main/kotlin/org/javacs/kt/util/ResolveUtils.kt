/*
 * Source: https://github.com/JetBrains/kotlin-web-demo/blob/master/versions/1.2.50/src/main/java/org/jetbrains/webdemo/kotlin/impl/ResolveUtils.kt
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.javacs.kt.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.config.TargetPlatformVersion
import org.jetbrains.kotlin.container.*
import org.jetbrains.kotlin.context.ContextForNewModule
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.JsConfig
import org.jetbrains.kotlin.js.resolve.JsPlatform
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.FileScopeProviderImpl
import org.jetbrains.kotlin.resolve.lazy.KotlinCodeAnalyzer
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.javacs.kt.Compiler
import java.util.*

object ResolveUtils {
	fun getBindingContext(files: List<KtFile>, compiler: Compiler): BindingContext {
		val result = analyzeFileForJvm(files, compiler)
		val analyzeExhaust = result.getFirst()
		return analyzeExhaust.bindingContext
	}

	fun getGenerationState(files: List<KtFile>, compiler: Compiler, compilerConfiguration: CompilerConfiguration): GenerationState {
		val analyzeExhaust = analyzeFileForJvm(files, compiler).getFirst()
		val project = compiler.environment.project
		return GenerationState.Builder(
				project,
				ClassBuilderFactories.binaries(false),
				analyzeExhaust.moduleDescriptor,
				analyzeExhaust.bindingContext,
				files,
				compilerConfiguration
		).build()
	}

	fun analyzeFileForJvm(files: List<KtFile>, compiler: Compiler): Pair<AnalysisResult, ComponentProvider> {
		val environment = compiler.environment
		val project = environment.project
		val trace = CliBindingTrace()
		val configuration = environment.configuration
		configuration.put(JVMConfigurationKeys.ADD_BUILT_INS_FROM_COMPILER_TO_DEPENDENCIES, true)

		val container = TopDownAnalyzerFacadeForJVM.createContainer(
				environment.project,
				files,
				trace,
				configuration,
				{ globalSearchScope -> environment.createPackagePartProvider(globalSearchScope) },
				{ storageManager, ktFiles -> FileBasedDeclarationProviderFactory(storageManager, ktFiles) },
				TopDownAnalyzerFacadeForJVM.newModuleSearchScope(project, files)
		)

		container.getService(LazyTopDownAnalyzer::class.java).analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files, DataFlowInfo.EMPTY)

		val moduleDescriptor = container.getService(ModuleDescriptor::class.java)
		for (extension in AnalysisHandlerExtension.getInstances(project)) {
			val result = extension.analysisCompleted(project, moduleDescriptor, trace, files)
			if (result != null) break
		}

		return Pair(
				AnalysisResult.success(trace.bindingContext, moduleDescriptor),
				container)
	}

	private fun computeDependencies(module: ModuleDescriptorImpl, config: JsConfig): List<ModuleDescriptorImpl> {
		val allDependencies = ArrayList<ModuleDescriptorImpl>()
		allDependencies.add(module)
		config.moduleDescriptors.mapTo(allDependencies) { it.data }
		allDependencies.add(JsPlatform.builtIns.builtInsModule)
		return allDependencies
	}

	private fun createContainerForTopDownAnalyzerForJs(
			moduleContext: ModuleContext,
			bindingTrace: BindingTrace,
			platformVersion: TargetPlatformVersion,
			declarationProviderFactory: DeclarationProviderFactory
	): Pair<LazyTopDownAnalyzer, ComponentProvider> {
		val container = composeContainer(
				"TopDownAnalyzerForJs",
				JsPlatform.platformConfigurator.platformSpecificContainer
		) {
			configureModule(moduleContext, JsPlatform, platformVersion, bindingTrace)
			useInstance(declarationProviderFactory)
			registerSingleton(AnnotationResolverImpl::class.java)
			registerSingleton(FileScopeProviderImpl::class.java)

			CompilerEnvironment.configure(this)

			useInstance(LookupTracker.DO_NOTHING)
			useInstance(LanguageVersionSettingsImpl.DEFAULT)
			registerSingleton(ResolveSession::class.java)
			registerSingleton(LazyTopDownAnalyzer::class.java)
		}

		container.getService(ModuleDescriptorImpl::class.java).initialize(container.getService(KotlinCodeAnalyzer::class.java).getPackageFragmentProvider())

		return Pair(container.getService(LazyTopDownAnalyzer::class.java), container)
	}
}
