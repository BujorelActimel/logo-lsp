package com.logolsp.features

import com.logolsp.analysis.DocumentAnalysis
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.WorkspaceEdit

class ChangeSignatureProvider(private val analysis: DocumentAnalysis) {
    fun codeActionsAt(uri: String, pos: Position): List<CodeAction> {
        // TODO: implement
        return emptyList()
    }

    fun computeSyncEdit(uri: String, procName: String): WorkspaceEdit? {
        // TODO: implement
        return null
    }
}
