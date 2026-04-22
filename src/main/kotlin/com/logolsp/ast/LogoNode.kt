package com.logolsp.ast

import org.eclipse.lsp4j.Range

sealed class LogoNode {
    abstract val range: Range
}

data class ProcedureDef(
    val name: String,
    val nameRange: Range,
    val params: List<Param>,
    val body: List<LogoNode>,
    override val range: Range,
) : LogoNode()

data class Param(
    val name: String,
    override val range: Range,
) : LogoNode()

data class MakeStmt(
    val varName: String,
    val varNameRange: Range,
    val value: LogoNode,
    override val range: Range,
) : LogoNode()

data class ProcedureCall(
    val name: String,
    val nameRange: Range,
    val args: List<LogoNode>,
    override val range: Range,
) : LogoNode()

data class RepeatStmt(
    val count: LogoNode,
    val body: List<LogoNode>,
    override val range: Range,
) : LogoNode()

data class IfStmt(
    val condition: LogoNode,
    val thenBranch: List<LogoNode>,
    val elseBranch: List<LogoNode>?,
    override val range: Range,
) : LogoNode()

data class OutputStmt(
    val value: LogoNode,
    override val range: Range,
) : LogoNode()

data class StopStmt(
    override val range: Range,
) : LogoNode()

data class ForStmt(
    val varName: String,
    val start: LogoNode,
    val stop: LogoNode,
    val step: LogoNode?,
    val body: List<LogoNode>,
    override val range: Range,
) : LogoNode()

data class VarRef(
    val name: String,
    override val range: Range,
) : LogoNode()

data class NumberLit(
    val value: Double,
    override val range: Range,
) : LogoNode()

data class WordLit(
    val value: String,
    override val range: Range,
) : LogoNode()

data class BinaryOp(
    val op: String,
    val left: LogoNode,
    val right: LogoNode,
    override val range: Range,
) : LogoNode()

data class BlockExpr(
    val statements: List<LogoNode>,
    override val range: Range,
) : LogoNode()

data class BuiltinCall(
    val name: String,
    val nameRange: Range,
    val args: List<LogoNode>,
    override val range: Range,
) : LogoNode()
