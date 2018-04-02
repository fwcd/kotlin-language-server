package org.javacs.kt

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.cli.jvm.compiler.CliLightClassGenerationSupport.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.container.getValue
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.data.KtClassLikeInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.*
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import java.nio.file.Path

class Compiler(private val allFiles: Collection<KtFile>) {
    private val trace = CliBindingTrace()
    private val container = TopDownAnalyzerFacadeForJVM.createContainer(
            project = env.project,
            files = listOf(),
            trace = trace,
            configuration = env.configuration,
            packagePartProvider = env::createPackagePartProvider,
            declarationProviderFactory = { storageManager, _ ->  DeclarationFinder(storageManager, allFiles) })
    private val resolveSession = container.get<ResolveSession>()
    private val finder = container.get<DeclarationFinder>()
    private val topDownAnalyzer = container.get<LazyTopDownAnalyzer>()

    fun compileFully(vararg files: KtFile): BindingContext {
        checkExists(*files)
        topDownAnalyzer.analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files.asList())
        return trace.bindingContext
    }

    private fun checkExists(vararg files: KtFile) {
        val filesSet = files.map { it.virtualFilePath }.toSet()
        val activeSet = allFiles.map { it.virtualFilePath }.toSet()
        val notActive = filesSet - activeSet

        if (notActive.isNotEmpty()) {
            fun describe(files: Collection<String>) =
                    if (files.size < 5) files.joinToString(", ")
                    else "${files.size} files"
            val message = "${describe(notActive)} not in this compiler's active set ${describe(activeSet)}"

            throw IllegalStateException(message)
        }
    }

    // Incremental compilation
    private val incrementalCompiler: ExpressionTypingServices by container
    private val openFiles = mutableMapOf<Path, KtFile>()

    fun openForEditing(file: Path, content: String): KtFile {
        val kt = createFile(file, content)

        openFiles[file] = kt
        finder.setOpenFiles(openFiles.values)

        return kt
    }

    fun close(file: Path) {
        openFiles.remove(file)
        finder.setOpenFiles(openFiles.values)
    }

    fun compileIncrementally(expression: KtExpression, scopeWithImports: LexicalScope): BindingContext {
        val trace = BindingTraceContext()
        incrementalCompiler.getTypeInfo(
                scopeWithImports, expression, TypeUtils.NO_EXPECTED_TYPE, DataFlowInfo.EMPTY, trace, true)
        return trace.bindingContext
    }

    companion object {
        private val config = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, JvmAbi.DEFAULT_MODULE_NAME)
        }
        private val env = KotlinCoreEnvironment.createForProduction(
                parentDisposable = Disposable { },
                configuration = config,
                configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val parser = KtPsiFactory(env.project)
        private val localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)

        fun openFile(path: Path): KtFile {
            val absolutePath = path.toAbsolutePath().toString()
            val virtualFile = localFileSystem.findFileByPath(absolutePath)
                              ?: throw RuntimeException("Couldn't find $path")
            return PsiManager.getInstance(env.project).findFile(virtualFile) as KtFile
        }

        private fun createFile(file: Path, content: String): KtFile {
            val absolutePath = file.toAbsolutePath().toString().substring(1)
            val language = Language.findLanguageByID("kotlin")!!
            return PsiFileFactory.getInstance(env.project).createFileFromText(absolutePath, language, content) as KtFile
        }

        fun fromPaths(sourceFiles: Collection<Path>) =
                Compiler(sourceFiles.map(::openFile))
    }
}

private class DeclarationFinder(
        private val storageManager: StorageManager,
        private val allFiles: Collection<KtFile>): DeclarationProviderFactory {
    private val onDisk = FileBasedDeclarationProviderFactory(storageManager, allFiles)
    private var inMemory = DeclarationProviderFactory.EMPTY

    fun setOpenFiles(files: Collection<KtFile>) {
        inMemory = FileBasedDeclarationProviderFactory(storageManager, files)
    }

    override fun diagnoseMissingPackageFragment(name: FqName, file: KtFile?) {
        inMemory.diagnoseMissingPackageFragment(name, file)
        onDisk.diagnoseMissingPackageFragment(name, file)
    }

    override fun getClassMemberDeclarationProvider(info: KtClassLikeInfo): ClassMemberDeclarationProvider {
        return PsiBasedClassMemberDeclarationProvider(storageManager, info)
    }

    override fun getPackageMemberDeclarationProvider(name: FqName): PackageMemberDeclarationProvider {
        val memory = InMemoryPackageFinder(name)
        val disk = onDisk.getPackageMemberDeclarationProvider(name)
        return CombinedPackageMemberDeclarationProvider(listOfNotNull(memory, disk))
    }

    inner class InMemoryPackageFinder(private val name: FqName): PackageMemberDeclarationProvider {
        private var cachedInMemory = inMemory
        private var cachedDelegate = refreshDelegate()

        private fun refreshDelegate(): PackageMemberDeclarationProvider? =
                inMemory.getPackageMemberDeclarationProvider(name)

        private fun delegate(): PackageMemberDeclarationProvider? {
            if (cachedInMemory !== inMemory) {
                cachedDelegate = refreshDelegate()
                cachedInMemory = inMemory
            }

            return cachedDelegate
        }

        override fun getAllDeclaredSubPackages(nameFilter: (Name) -> Boolean) =
                delegate()?.getAllDeclaredSubPackages(nameFilter) ?: emptyList()

        override fun getPackageFiles() =
                delegate()?.getPackageFiles() ?: emptyList()

        override fun containsFile(file: KtFile) =
                delegate()?.containsFile(file) ?: false

        override fun getDeclarations(kindFilter: DescriptorKindFilter, nameFilter: (Name) -> Boolean) =
                delegate()?.getDeclarations(kindFilter, nameFilter) ?: emptyList()

        override fun getFunctionDeclarations(name: Name) =
                delegate()?.getFunctionDeclarations(name) ?: emptyList()

        override fun getPropertyDeclarations(name: Name) =
                delegate()?.getPropertyDeclarations(name) ?: emptyList()

        override fun getDestructuringDeclarationsEntries(name: Name): Collection<KtDestructuringDeclarationEntry> =
                delegate()?.getDestructuringDeclarationsEntries(name) ?: emptyList()

        override fun getClassOrObjectDeclarations(name: Name) =
                delegate()?.getClassOrObjectDeclarations(name) ?: emptyList()

        override fun getTypeAliasDeclarations(name: Name) =
                delegate()?.getTypeAliasDeclarations(name) ?: emptyList()

        override fun getDeclarationNames(): Set<Name> =
                delegate()?.getDeclarationNames() ?: emptySet()
    }
}