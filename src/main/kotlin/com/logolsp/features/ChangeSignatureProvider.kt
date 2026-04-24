package com.logolsp.features

import com.logolsp.analysis.DocumentAnalysis
import org.eclipse.lsp4j.*

class ChangeSignatureProvider(private val analysis: DocumentAnalysis) {

    /**
     * Offer "Apply signature change to all callers" when the cursor is on
     * the TO definition line of a procedure.
     */
    fun codeActionsAt(uri: String, pos: Position): List<CodeAction> {
        val proc = analysis.symbolTable.procedureAt(pos) ?: return emptyList()
        val action = CodeAction("Apply signature change to all callers").apply {
            kind = CodeActionKind.Refactor
            command = Command(
                "Apply signature change to all callers",
                "logo.syncCallers",
                listOf(uri, proc.name)
            )
        }
        return listOf(action)
    }

    /**
     * Compute workspace edits that reconcile each call site of [procName]
     * with the procedure's current parameter count in [analysis].
     *
     * - Too few args  → append " 0" for each missing arg
     * - Too many args → remove trailing excess args
     * - Exact match   → no edit for that site
     *
     * Returns null if no edits are needed.
     */
    fun computeSyncEdit(uri: String, procName: String): WorkspaceEdit? {
        val proc = analysis.symbolTable.procedures[procName.uppercase()] ?: return null
        val newArity = proc.params.size
        val sites = analysis.symbolTable.callSitesOf(procName)

        val edits = mutableListOf<TextEdit>()
        for (site in sites) {
            val delta = newArity - site.args.size
            when {
                delta > 0 -> {
                    // Append placeholder(s) after the last arg (or after the proc name)
                    val insertPos = if (site.args.isNotEmpty())
                        site.args.last().end
                    else
                        site.nameRange.end
                    val placeholder = (" 0").repeat(delta)
                    edits += TextEdit(Range(insertPos, insertPos), placeholder)
                }
                delta < 0 -> {
                    val removeCount = -delta
                    val firstToRemove = site.args[site.args.size - removeCount]
                    val lastToRemove  = site.args.last()
                    // Include the whitespace before the first removed arg
                    val removeStart = extendLeftOverWhitespace(firstToRemove.start)
                    val removeRange = Range(removeStart, lastToRemove.end)
                    edits += TextEdit(removeRange, "")
                }
                // delta == 0 → no edit needed
            }
        }

        if (edits.isEmpty()) return null
        return WorkspaceEdit(mapOf(uri to edits))
    }

    /**
     * Walk backwards on the same line from [pos] to include any preceding
     * whitespace in the deletion range.
     */
    private fun extendLeftOverWhitespace(pos: Position): Position {
        val lines = analysis.source.lines()
        val line = lines.getOrNull(pos.line) ?: return pos
        var col = pos.character
        while (col > 0 && line[col - 1].isWhitespace()) col--
        return Position(pos.line, col)
    }
}
