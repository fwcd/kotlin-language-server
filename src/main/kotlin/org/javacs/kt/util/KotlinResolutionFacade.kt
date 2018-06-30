/*
 * Source: https://github.com/JetBrains/kotlin-web-demo/blob/master/versions/1.2.50/src/main/java/org/jetbrains/webdemo/kotlin/impl/KotlinResolutionFacade.kt
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
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinResolutionFacade(
	private val provider: ComponentProvider?,
	override val project: Project
): ResolutionFacade {
	override fun <T : Any> getFrontendService(element: PsiElement, serviceClass: Class<T>): T {
		throw UnsupportedOperationException()
	}

	override fun <T : Any> getFrontendService(serviceClass: Class<T>): T {
		return if (provider == null)
			throw IllegalArgumentException("Provider is null in 'getFrontendService'")
		else provider.resolve(serviceClass)!!.getValue() as T
	}

	override fun <T : Any> getFrontendService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T {
		throw UnsupportedOperationException()
	}

	override fun <T : Any> getIdeService(serviceClass: Class<T>): T {
		throw UnsupportedOperationException()
	}

	override fun <T : Any> tryGetFrontendService(element: PsiElement, serviceClass: Class<T>): T? {
		throw UnsupportedOperationException()
	}

	override val moduleDescriptor: ModuleDescriptor
		get() = throw UnsupportedOperationException()

	override fun analyze(element: KtElement, bodyResolveMode: BodyResolveMode): BindingContext {
		throw UnsupportedOperationException()
	}

	override fun analyzeWithAllCompilerChecks(elements: Collection<KtElement>): AnalysisResult {
		throw UnsupportedOperationException()
	}

	override fun resolveToDescriptor(declaration: KtDeclaration, bodyResolveMode: BodyResolveMode): DeclarationDescriptor {
		throw UnsupportedOperationException()
	}

	override fun analyze(elements: Collection<KtElement>, bodyResolveMode: BodyResolveMode): BindingContext {
		throw UnsupportedOperationException()
	}
}
