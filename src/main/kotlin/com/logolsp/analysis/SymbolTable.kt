package com.logolsp.analysis

import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

data class ProcedureDef(
    val name: String,
    val params: List<String>,
    val nameRange: Range,
    val range: Range,
)

data class CallSite(
    val procName: String,
    val nameRange: Range,
    val args: List<Range>,
)

data class VariableDef(
    val name: String,
    val range: Range,
)

class SymbolTable {
    val procedures: Map<String, ProcedureDef> = emptyMap()
    val callSites: List<CallSite> = emptyList()
    val variables: Map<String, VariableDef> = emptyMap()

    fun procedureAt(pos: Position): ProcedureDef? = null
    fun callSitesOf(name: String): List<CallSite> = emptyList()
    fun variableAt(pos: Position): VariableDef? = null
}
