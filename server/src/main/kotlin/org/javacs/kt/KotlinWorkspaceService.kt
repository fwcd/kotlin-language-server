package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.javacs.kt.symbols.workspaceSymbols
import org.javacs.kt.command.JAVA_TO_KOTLIN_COMMAND
import org.javacs.kt.j2k.convertJavaToKotlin
import org.javacs.kt.position.extractRange
import org.javacs.kt.util.filePath
import org.javacs.kt.util.parseURI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import com.google.gson.JsonElement
import com.google.gson.Gson
import com.google.gson.JsonObject

class KotlinWorkspaceService(
    private val sf: SourceFiles,
    private val sp: SourcePath,
    private val cp: CompilerClassPath,
    private val docService: KotlinTextDocumentService,
    private val config: Configuration
) : WorkspaceService, LanguageClientAware {
    private val gson = Gson()
    private var languageClient: LanguageClient? = null

    override fun connect(client: LanguageClient): Unit {
        languageClient = client
    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        val args = params.arguments
        LOG.info("Executing command: {} with {}", params.command, params.arguments)

        when (params.command) {
            JAVA_TO_KOTLIN_COMMAND -> {
                val fileUri = gson.fromJson(args[0] as JsonElement, String::class.java)
                val range = gson.fromJson(args[1] as JsonElement, Range::class.java)

                val selectedJavaCode = extractRange(sp.content(parseURI(fileUri)), range)
                val kotlinCode = convertJavaToKotlin(selectedJavaCode, cp.compiler)

                languageClient?.applyEdit(ApplyWorkspaceEditParams(WorkspaceEdit(listOf(Either.forLeft<TextDocumentEdit, ResourceOperation>(
                    TextDocumentEdit(
                        VersionedTextDocumentIdentifier().apply { uri = fileUri },
                        listOf(TextEdit(range, kotlinCode))
                    )
                )))))
            }
        }

        return CompletableFuture.completedFuture(null)
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        for (change in params.changes) {
            val uri = parseURI(change.uri)
            val path = uri.filePath

            when (change.type) {
                FileChangeType.Created -> {
                    sf.createdOnDisk(uri)
                    path?.let(cp::createdOnDisk)?.let { if (it) sp.refresh() }
                }
                FileChangeType.Deleted -> {
                    sf.deletedOnDisk(uri)
                    path?.let(cp::deletedOnDisk)?.let { if (it) sp.refresh() }
                }
                FileChangeType.Changed -> {
                    sf.changedOnDisk(uri)
                    path?.let(cp::changedOnDisk)?.let { if (it) sp.refresh() }
                }
                null -> {
                    // Nothing to do
                }
            }
        }
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        val settings = params.settings as? JsonObject
        settings?.get("kotlin")?.asJsonObject?.apply {
            // Update deprecated configuration keys
            get("debounceTime")?.asLong?.let {
                config.linting.debounceTime = it
                docService.updateDebouncer()
            }
            get("snippetsEnabled")?.asBoolean?.let { config.completion.snippets.enabled = it }

            // Update compiler options
            get("compiler")?.asJsonObject?.apply {
                val compiler = config.compiler
                get("jvm")?.asJsonObject?.apply {
                    val jvm = compiler.jvm
                    get("target")?.asString?.let {
                        jvm.target = it
                        cp.updateCompilerConfiguration()
                    }
                }
            }

            // Update linter options
            get("linting")?.asJsonObject?.apply {
                val linting = config.linting
                get("debounceTime")?.asLong?.let {
                    linting.debounceTime = it
                    docService.updateDebouncer()
                }
            }

            // Update code-completion options
            get("completion")?.asJsonObject?.apply {
                val completion = config.completion
                get("snippets")?.asJsonObject?.apply {
                    val snippets = completion.snippets
                    get("enabled")?.asBoolean?.let { snippets.enabled = it }
                }
            }

            // Update indexing options
            get("indexing")?.asJsonObject?.apply {
                val indexing = config.indexing
                get("enabled")?.asBoolean?.let {
                    indexing.enabled = it
                }
            }

            // Update options about external sources e.g. JAR files, decompilers, etc
            get("externalSources")?.asJsonObject?.apply {
                val externalSources = config.externalSources
                get("useKlsScheme")?.asBoolean?.let { externalSources.useKlsScheme = it }
                get("autoConvertToKotlin")?.asBoolean?.let { externalSources.autoConvertToKotlin = it }
            }
        }

        LOG.info("Updated configuration: {}", settings)
    }

    @Suppress("DEPRECATION")
    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> {
        val result = workspaceSymbols(params.query, sp)

        return CompletableFuture.completedFuture(Either.forRight(result))
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        for (change in params.event.added) {
            LOG.info("Adding workspace {} to source path", change.uri)

            val root = Paths.get(parseURI(change.uri))

            sf.addWorkspaceRoot(root)
            val refreshed = cp.addWorkspaceRoot(root)
            if (refreshed) {
                sp.refresh()
            }
        }
        for (change in params.event.removed) {
            LOG.info("Dropping workspace {} from source path", change.uri)

            val root = Paths.get(parseURI(change.uri))

            sf.removeWorkspaceRoot(root)
            val refreshed = cp.removeWorkspaceRoot(root)
            if (refreshed) {
                sp.refresh()
            }
        }
    }
}
