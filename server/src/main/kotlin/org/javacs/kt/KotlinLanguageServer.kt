package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.javacs.kt.commands.ALL_COMMANDS
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class KotlinLanguageServer : LanguageServer, LanguageClientAware {
    val classPath = CompilerClassPath()
    val sourcePath = SourcePath(classPath)
    val sourceFiles = SourceFiles(sourcePath)
    private val config = Configuration()
    private val textDocuments = KotlinTextDocumentService(sourceFiles, sourcePath, config)
    private val workspaces = KotlinWorkspaceService(sourceFiles, sourcePath, classPath, textDocuments, config)
    
    override fun connect(client: LanguageClient) {
        connectLoggingBackend(client)
        
        workspaces.connect(client)
        textDocuments.connect(client)
        
        LOG.info("Connected to client")
    }

    override fun shutdown(): CompletableFuture<Any> {
        return completedFuture(null)
    }

    override fun getTextDocumentService(): KotlinTextDocumentService {
        return textDocuments
    }

    override fun exit() {
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val serverCapabilities = ServerCapabilities()
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental)
        serverCapabilities.workspace = WorkspaceServerCapabilities()
        serverCapabilities.workspace.workspaceFolders = WorkspaceFoldersOptions()
        serverCapabilities.workspace.workspaceFolders.supported = true
        serverCapabilities.workspace.workspaceFolders.changeNotifications = Either.forRight(true)
        serverCapabilities.hoverProvider = true
        serverCapabilities.completionProvider = CompletionOptions(false, listOf("."))
        serverCapabilities.signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))
        serverCapabilities.definitionProvider = true
        serverCapabilities.documentSymbolProvider = true
        serverCapabilities.workspaceSymbolProvider = true
        serverCapabilities.referencesProvider = true
        serverCapabilities.codeActionProvider = true
        serverCapabilities.executeCommandProvider = ExecuteCommandOptions(ALL_COMMANDS)

        val clientCapabilities = params.capabilities
        config.snippetsEnabled = clientCapabilities?.textDocument?.completion?.completionItem?.snippetSupport ?: false

        if (params.rootUri != null) {
            LOG.info("Adding workspace {} to source path", params.rootUri)

            val root = Paths.get(URI.create(params.rootUri))

            sourceFiles.addWorkspaceRoot(root)
            classPath.addWorkspaceRoot(root)
        }

        return completedFuture(InitializeResult(serverCapabilities))
    }

    override fun getWorkspaceService(): KotlinWorkspaceService {
        return workspaces
    }
    
    private fun connectLoggingBackend(client: LanguageClient) {
        val backend: (LogMessage) -> Unit = {
            client.logMessage(MessageParams().apply {
                type = it.level.toLSPMessageType()
                message = it.message
            })
        }
        LOG.connectOutputBackend(backend)
        LOG.connectErrorBackend(backend)
    }
    
    private fun LogLevel.toLSPMessageType(): MessageType = when (this) {
        LogLevel.ERROR -> MessageType.Error
        LogLevel.WARN -> MessageType.Warning
        LogLevel.INFO -> MessageType.Info
        else -> MessageType.Log
    }
}
