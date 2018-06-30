/*
 * Source: https://github.com/JetBrains/kotlin-web-demo/blob/master/versions/1.2.50/src/main/java/org/jetbrains/webdemo/kotlin/impl/completion/CompletionProvider.kt
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
package org.javacs.kt.completion

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl
import org.jetbrains.kotlin.idea.codeInsight.ReferenceVariantsHelper
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.expressions.KotlinTypeInfo
import org.jetbrains.kotlin.types.isFlexible
import org.javacs.kt.util.KotlinResolutionFacade
import org.javacs.kt.util.JetPsiFactoryUtil
import org.javacs.kt.util.ResolveUtils
import org.javacs.kt.util.KotlinLSException
import org.javacs.kt.Compiler
import java.beans.PropertyDescriptor
import java.util.Collections

private const val DELIMITER = "KtD3l1m1t3r"

class CompletionProvider(
	private val psiFiles: MutableList<KtFile>,
	private var currentPsiFile: KtFile?,
	private val lineNumber: Int,
	private val charNumber: Int,
	private val compiler: Compiler
) {
	private val NUMBER_OF_CHAR_IN_COMPLETION_NAME = 40
	private val currentProject: Project
	private var currentDocument: Document? = null
	private var caretPositionOffset: Int = 0

	private val expressionForScope: PsiElement?
		get() {
			var element = currentPsiFile!!.findElementAt(caretPositionOffset)
			while (element !is KtExpression && element != null) {
				element = element.parent
			}
			return element
		}

	init {
		this.currentProject = currentPsiFile!!.project
		this.currentDocument = currentPsiFile!!.viewProvider.document
	}

	fun getResult(): List<CompletionVariant> {
		try {
			addExpressionAtCaret()
			val analysisResult: AnalysisResult
			val bindingContext: BindingContext
			val containerProvider: ComponentProvider
			val resolveResult = ResolveUtils.analyzeFileForJvm(psiFiles, compiler)
			analysisResult = resolveResult.first
			bindingContext = analysisResult.bindingContext

			containerProvider = resolveResult.getSecond()

			val element = expressionForScope ?: return emptyList()
			var descriptors: Collection<DeclarationDescriptor>? = null
			var isTipsManagerCompletion = true

			val helper = ReferenceVariantsHelper(
					bindingContext,
					KotlinResolutionFacade(containerProvider, currentProject),
					analysisResult.moduleDescriptor,
					VISIBILITY_FILTER,
					emptySet()
			)

			when {
				element is KtSimpleNameExpression -> {
					descriptors = helper.getReferenceVariants(element, DescriptorKindFilter.ALL, NAME_FILTER, true, true, true, null)
				}
				element.parent is KtSimpleNameExpression -> {
					descriptors = helper.getReferenceVariants(element.parent as KtSimpleNameExpression, DescriptorKindFilter.ALL, NAME_FILTER, true, true, true, null)
				}
				else -> {
					isTipsManagerCompletion = false
					val resolutionScope: LexicalScope?
					val parent = element.parent

					when (parent) {
						is KtQualifiedExpression -> {
							val receiverExpression = parent.receiverExpression
							val expressionType = bindingContext.get<KtExpression, KotlinTypeInfo>(BindingContext.EXPRESSION_TYPE_INFO, receiverExpression)!!.type
							resolutionScope = bindingContext.get<KtElement, LexicalScope>(BindingContext.LEXICAL_SCOPE, receiverExpression)

							if (expressionType != null && resolutionScope != null) {
								descriptors = expressionType.memberScope.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER)
							}
						}
						else -> {
							resolutionScope = bindingContext.get<KtElement, LexicalScope>(BindingContext.LEXICAL_SCOPE, element as KtExpression)
							if (resolutionScope != null) {
								descriptors = resolutionScope.getContributedDescriptors(DescriptorKindFilter.ALL, MemberScope.ALL_NAME_FILTER)
							} else {
								return emptyList()
							}
						}
					}
				}
			}

			val result = ArrayList<CompletionVariant>()

			if (descriptors != null) {
				var prefix: String
				if (!isTipsManagerCompletion) {
					prefix = element.parent.text
				} else {
					prefix = element.text
				}
				prefix = prefix.substringBefore(DELIMITER, prefix)
				if (prefix.endsWith(".")) {
					prefix = ""
				}

				if (descriptors !is ArrayList<*>) {
					descriptors = ArrayList(descriptors)
				}

				Collections.sort((descriptors as ArrayList<DeclarationDescriptor>?)!!) { d1, d2 ->
					val d1PresText = getPresentableText(d1)
					val d2PresText = getPresentableText(d2)
					(d1PresText.getFirst() + d1PresText.getSecond()).compareTo(d2PresText.getFirst() + d2PresText.getSecond(), ignoreCase = true)
				}

				for (descriptor in descriptors) {
					val presentableText = getPresentableText(descriptor)

					val fullName = formatName(presentableText.getFirst(), NUMBER_OF_CHAR_IN_COMPLETION_NAME)
					var completionText = fullName
					var position = completionText.indexOf('(')
					if (position != -1) {
						// If this is a string with a package after
						if (completionText[position - 1] == ' ') {
							position -= 2
						}
						// If this is a method without args
						if (completionText[position + 1] == ')') {
							position++
						}
						completionText = completionText.substring(0, position + 1)
					}
					position = completionText.indexOf(":")
					if (position != -1) {
						completionText = completionText.substring(0, position - 1)
					}

					if (prefix.isEmpty() || fullName.startsWith(prefix)) {
						val completionVariant = CompletionVariant(completionText, fullName, presentableText.getSecond(), descriptor)
						result.add(completionVariant)
					}
				}

				result.addAll(keywordsCompletionVariants(KtTokens.KEYWORDS, prefix))
				result.addAll(keywordsCompletionVariants(KtTokens.SOFT_KEYWORDS, prefix))
			}

			return result
		} catch (e: Throwable) {
			throw KotlinLSException(e)
		}
	}

	private fun formatName(builder: String, symbols: Int): String {
		return if (builder.length > symbols) {
			builder.substring(0, symbols) + "..."
		} else builder
	}

	private fun addExpressionAtCaret() {
		caretPositionOffset = getOffsetFromLineAndChar(lineNumber, charNumber)
		val text = currentPsiFile!!.text
		if (caretPositionOffset != 0) {
			val buffer = StringBuilder(text.substring(0, caretPositionOffset))
			buffer.append("$DELIMITER ")
			buffer.append(text.substring(caretPositionOffset))
			psiFiles.remove(currentPsiFile!!)
			currentPsiFile = JetPsiFactoryUtil.createFile(currentProject, currentPsiFile!!.name, buffer.toString())
			psiFiles.add(currentPsiFile!!)
			currentDocument = currentPsiFile!!.viewProvider.document
		}
	}

	private fun getOffsetFromLineAndChar(line: Int, charNumber: Int): Int {
		val lineStart = currentDocument!!.getLineStartOffset(line)
		return lineStart + charNumber
	}

	private fun keywordsCompletionVariants(keywords: TokenSet, prefix: String): List<CompletionVariant> {
		return keywords.types
				.map { (it as KtKeywordToken).value }
				.filter { it.startsWith(prefix) }
				.mapTo(ArrayList()) { CompletionVariant(it, it, "", null) }
	}


	private val RENDERER = IdeDescriptorRenderers.SOURCE_CODE.withOptions {
		classifierNamePolicy = ClassifierNamePolicy.SHORT
		typeNormalizer = IdeDescriptorRenderers.APPROXIMATE_FLEXIBLE_TYPES
		parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
		typeNormalizer = { kotlinType: KotlinType ->
			if (kotlinType.isFlexible()) {
				kotlinType.asFlexibleType().upperBound
			} else kotlinType
		}
	}

	private val VISIBILITY_FILTER = fun(descriptor: DeclarationDescriptor): Boolean {
		return true
	}
	private val NAME_FILTER = fun(name: Name): Boolean {
		return true
	}

	// see DescriptorLookupConverter.createLookupElement
	private fun getPresentableText(descriptor: DeclarationDescriptor): Pair<String, String> {
		var presentableText = descriptor.name.asString()
		var typeText = ""
		var tailText = ""

		if (descriptor is FunctionDescriptor) {
			val returnType = descriptor.returnType
			typeText = if (returnType != null) RENDERER.renderType(returnType) else ""
			presentableText += RENDERER.renderFunctionParameters(descriptor)

			val extensionFunction = descriptor.extensionReceiverParameter != null
			val containingDeclaration = descriptor.containingDeclaration
			if (containingDeclaration != null && extensionFunction) {
				tailText += " for " + RENDERER.renderType(descriptor.extensionReceiverParameter!!.type)
				tailText += " in " + DescriptorUtils.getFqName(containingDeclaration)
			}
		} else if (descriptor is VariableDescriptor) {
			val outType = descriptor.type
			typeText = RENDERER.renderType(outType)
		} else if (descriptor is ClassDescriptor) {
			val declaredIn = descriptor.containingDeclaration
			tailText = " (" + DescriptorUtils.getFqName(declaredIn) + ")"
		} else {
			typeText = RENDERER.render(descriptor)
		}

		return if (typeText.isEmpty()) {
			Pair(presentableText, tailText)
		} else {
			Pair(presentableText, typeText)
		}
	}
}
