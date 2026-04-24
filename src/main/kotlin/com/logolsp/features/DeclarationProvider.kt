package com.logolsp.features

import com.logolsp.analysis.DocumentAnalysis
import com.logolsp.analysis.containsPos
import com.logolsp.parser.TokenType
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position

class DeclarationProvider(private val analysis: DocumentAnalysis) {

    /**
     * Returns the declaration [Location] for the symbol at [pos], or null.
     *
     * - Cursor on a call site name → jumps to the procedure's TO line.
     * - Cursor on a definition name → returns the definition itself.
     * - Cursor on a :variable → jumps to the first MAKE assignment site.
     */
    fun declarationAt(uri: String, pos: Position): Location? {
        val st = analysis.symbolTable

        val callSite = st.callSites.firstOrNull { containsPos(it.nameRange, pos) }
        if (callSite != null) {
            val proc = st.procedures[callSite.procName]
            if (proc != null) return Location(uri, proc.nameRange)
        }

        val proc = st.procedures.values.firstOrNull { containsPos(it.nameRange, pos) }
        if (proc != null) return Location(uri, proc.nameRange)

        // Cursor on a :varName reference → jump to MAKE definition
        val varRefToken = analysis.tokens.firstOrNull { tok ->
            tok.type == TokenType.COLON_WORD &&
            pos.line == tok.line && pos.character >= tok.col && pos.character <= tok.endCol
        }
        if (varRefToken != null) {
            val varDef = st.variables[varRefToken.value.removePrefix(":")]
            if (varDef != null) return Location(uri, varDef.range)
        }

        // Cursor on the MAKE "varName definition site itself
        val varDef = st.variableAt(pos)
        if (varDef != null) return Location(uri, varDef.range)

        return null
    }
}
