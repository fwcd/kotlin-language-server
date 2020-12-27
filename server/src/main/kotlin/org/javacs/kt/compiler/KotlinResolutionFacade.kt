package org.javacs.kt.compiler

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.ResolverForProject
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

@OptIn(FrontendInternals::class)
class KotlinResolutionFacade(
    override val project: Project,
    override val moduleDescriptor: ModuleDescriptor,
    private val componentProvider: ComponentProvider
) : ResolutionFacade {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getFrontendService(serviceClass: Class<T>) = componentProvider.resolve(serviceClass)?.getValue() as T

    override fun analyze(elements: Collection<KtElement>, bodyResolveMode: BodyResolveMode): BindingContext = throw UnsupportedOperationException()

    override fun analyze(element: KtElement, bodyResolveMode: BodyResolveMode): BindingContext = throw UnsupportedOperationException()

    override fun analyzeWithAllCompilerChecks(elements: Collection<KtElement>): AnalysisResult = throw UnsupportedOperationException()

    override fun <T : Any> getFrontendService(element: PsiElement, serviceClass: Class<T>): T = throw UnsupportedOperationException()

    override fun <T : Any> getFrontendService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T = throw UnsupportedOperationException()

    override fun <T : Any> getIdeService(serviceClass: Class<T>): T = TODO("not implemented")

    override fun getResolverForProject(): ResolverForProject<out ModuleInfo> = throw UnsupportedOperationException()

    override fun resolveToDescriptor(declaration: KtDeclaration, bodyResolveMode: BodyResolveMode): DeclarationDescriptor = throw UnsupportedOperationException()

    override fun <T : Any> tryGetFrontendService(element: PsiElement, serviceClass: Class<T>): T? = throw UnsupportedOperationException()
}
