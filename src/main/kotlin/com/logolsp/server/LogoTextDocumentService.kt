package com.logolsp.server

import com.logolsp.analysis.DocumentStore
import com.logolsp.features.ChangeSignatureProvider
import com.logolsp.features.DeclarationProvider
import com.logolsp.features.SemanticTokensProvider
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture

class LogoTextDocumentService(
    private val store: DocumentStore,
    private val client: () -> LogoLanguageClient,
) : TextDocumentService {

    override fun didOpen(params: DidOpenTextDocumentParams) {
        store.update(params.textDocument.uri, params.textDocument.text)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val text = params.contentChanges.lastOrNull()?.text ?: return
        store.update(params.textDocument.uri, text)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        store.remove(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        params.text?.let { store.update(params.textDocument.uri, it) }
    }

    override fun semanticTokensFull(
        params: SemanticTokensParams,
    ): CompletableFuture<SemanticTokens> {
        val analysis = store.get(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(SemanticTokens(emptyList()))
        return CompletableFuture.completedFuture(
            SemanticTokensProvider(analysis).provide()
        )
    }

    override fun declaration(
        params: DeclarationParams,
    ): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        val analysis = store.get(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
        val location = DeclarationProvider(analysis)
            .declarationAt(params.textDocument.uri, params.position)
        return CompletableFuture.completedFuture(Either.forLeft(listOfNotNull(location)))
    }

    override fun codeAction(
        params: CodeActionParams,
    ): CompletableFuture<List<Either<Command, CodeAction>>> {
        val analysis = store.get(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(emptyList())
        val actions = ChangeSignatureProvider(analysis)
            .codeActionsAt(params.textDocument.uri, params.range.start)
        return CompletableFuture.completedFuture(
            actions.map { Either.forRight<Command, CodeAction>(it) }
        )
    }
}
