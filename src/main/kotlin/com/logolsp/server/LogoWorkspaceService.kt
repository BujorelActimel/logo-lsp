package com.logolsp.server

import com.google.gson.JsonPrimitive
import com.logolsp.analysis.DocumentStore
import com.logolsp.features.ChangeSignatureProvider
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

class LogoWorkspaceService(
    private val store: DocumentStore,
    private val client: () -> LogoLanguageClient,
) : WorkspaceService {

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {}
    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {}

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        if (params.command != "logo.syncCallers") return done()

        // LSP4J deserializes command arguments as JsonPrimitive
        val uri      = (params.arguments?.getOrNull(0) as? JsonPrimitive)?.asString ?: return done()
        val procName = (params.arguments?.getOrNull(1) as? JsonPrimitive)?.asString ?: return done()

        val analysis = store.get(uri) ?: return done()
        val edit = ChangeSignatureProvider(analysis).computeSyncEdit(uri, procName) ?: return done()

        client().applyEdit(ApplyWorkspaceEditParams(edit))
        return done()
    }

    private fun done(): CompletableFuture<Any> = CompletableFuture.completedFuture(null)
}
