package com.logolsp.analysis

import com.logolsp.ast.BinaryOp
import com.logolsp.ast.BlockExpr
import com.logolsp.ast.BuiltinCall
import com.logolsp.ast.ForStmt
import com.logolsp.ast.IfStmt
import com.logolsp.ast.LogoNode
import com.logolsp.ast.MakeStmt
import com.logolsp.ast.NumberLit
import com.logolsp.ast.OutputStmt
import com.logolsp.ast.ProcedureDef as AstProcedureDef
import com.logolsp.ast.ProcedureCall
import com.logolsp.ast.RepeatStmt
import com.logolsp.ast.StopStmt
import com.logolsp.ast.VarRef
import com.logolsp.ast.WhileStmt
import com.logolsp.ast.WordLit
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

class SymbolTable(
    val procedures: Map<String, ProcedureDef> = emptyMap(),
    val callSites: List<CallSite> = emptyList(),
    val variables: Map<String, VariableDef> = emptyMap(),
) {
    /** The procedure whose definition header (TO line) contains [pos]. */
    fun procedureAt(pos: Position): ProcedureDef? =
        procedures.values.firstOrNull { pos.line == it.range.start.line }

    fun callSitesOf(name: String): List<CallSite> =
        callSites.filter { it.procName == name.uppercase() }

    fun variableAt(pos: Position): VariableDef? =
        variables.values.firstOrNull { containsPos(it.range, pos) }
}

class SymbolTableBuilder {
    private val procedures = mutableMapOf<String, ProcedureDef>()
    private val callSites  = mutableListOf<CallSite>()
    private val variables  = mutableMapOf<String, VariableDef>()

    fun build(nodes: List<LogoNode>): SymbolTable {
        nodes.forEach { walkTop(it) }
        return SymbolTable(procedures, callSites, variables)
    }

    private fun walkTop(node: LogoNode) {
        when (node) {
            is AstProcedureDef -> {
                procedures[node.name] = ProcedureDef(
                    node.name, node.params.map { it.name }, node.nameRange, node.range
                )
                node.body.forEach { walkBody(it) }
            }
            is MakeStmt -> {
                variables.getOrPut(node.varName) { VariableDef(node.varName, node.varNameRange) }
                walkBody(node.value)
            }
            is ProcedureCall -> recordCall(node)
            is BuiltinCall   -> node.args.forEach { walkBody(it) }
            else             -> walkBody(node)
        }
    }

    private fun walkBody(node: LogoNode) {
        when (node) {
            is ProcedureCall -> recordCall(node)
            is BuiltinCall   -> node.args.forEach { walkBody(it) }
            is MakeStmt -> {
                variables.getOrPut(node.varName) { VariableDef(node.varName, node.varNameRange) }
                walkBody(node.value)
            }
            is RepeatStmt    -> { walkBody(node.count); node.body.forEach { walkBody(it) } }
            is IfStmt        -> {
                walkBody(node.condition)
                node.thenBranch.forEach { walkBody(it) }
                node.elseBranch?.forEach { walkBody(it) }
            }
            is OutputStmt    -> walkBody(node.value)
            is ForStmt       -> {
                walkBody(node.start); walkBody(node.stop)
                node.step?.let { walkBody(it) }
                node.body.forEach { walkBody(it) }
            }
            is WhileStmt     -> { walkBody(node.condition); node.body.forEach { walkBody(it) } }
            is BinaryOp      -> { walkBody(node.left); walkBody(node.right) }
            is BlockExpr     -> node.statements.forEach { walkBody(it) }
            is AstProcedureDef -> walkTop(node) // nested (unusual but safe)
            else             -> { /* leaf nodes: VarRef, NumberLit, WordLit, StopStmt */ }
        }
    }

    private fun recordCall(node: ProcedureCall) {
        val argRanges = node.args.map { it.range }
        callSites += CallSite(node.name, node.nameRange, argRanges)
        node.args.forEach { walkBody(it) }
    }
}

internal fun containsPos(range: Range, pos: Position): Boolean {
    val start = range.start; val end = range.end
    if (pos.line < start.line || pos.line > end.line) return false
    if (pos.line == start.line && pos.character < start.character) return false
    if (pos.line == end.line   && pos.character > end.character)   return false
    return true
}
