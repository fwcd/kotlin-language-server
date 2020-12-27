package org.javacs.kt

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.compiler.JvmPackagePartProvider
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory

object CompilerFixtures {
    @JvmStatic fun createContainer(
            project: Project,
            files: Collection<KtFile>,
            trace: BindingTrace,
            configuration: CompilerConfiguration,
            packagePartProvider: (GlobalSearchScope) -> JvmPackagePartProvider): ComponentProvider {
        return TopDownAnalyzerFacadeForJVM.createContainer(
                project,
                emptyList(),
                trace,
                configuration,
                packagePartProvider,
                { storageManager, _ -> FileBasedDeclarationProviderFactory(storageManager, files) })
    }
}
