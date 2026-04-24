package com.logolsp.server

import com.logolsp.analysis.DocumentStore
import com.logolsp.features.SemanticTokensProvider
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class LogoLanguageServer : LanguageServer, LanguageClientAware {

    private var client: LanguageClient? = null
    private val store = DocumentStore()

    private val textDocumentService = LogoTextDocumentService(store) { client!! }
    private val workspaceService    = LogoWorkspaceService(store) { client!! }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val caps = ServerCapabilities().apply {
            textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)

            declarationProvider = Either.forLeft(true)
            definitionProvider = Either.forLeft(true)
            semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
                legend = SemanticTokensProvider.LEGEND
                setFull(true)
            }

            codeActionProvider = Either.forLeft(true)

            executeCommandProvider = ExecuteCommandOptions(listOf("logo.syncCallers"))
        }
        return CompletableFuture.completedFuture(InitializeResult(caps))
    }

    override fun initialized(params: InitializedParams) {}

    override fun shutdown(): CompletableFuture<Any> =
        CompletableFuture.completedFuture(null)

    override fun exit() {}

    override fun getTextDocumentService(): TextDocumentService = textDocumentService
    override fun getWorkspaceService(): WorkspaceService = workspaceService
}
