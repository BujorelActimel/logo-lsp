package com.logolsp.features

import com.logolsp.analysis.DocumentAnalysis
import com.logolsp.parser.TokenType
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensLegend

class SemanticTokensProvider(private val analysis: DocumentAnalysis) {

    companion object {
        val TOKEN_TYPES = listOf(
            "keyword", "function", "variable",
            "number", "string", "comment", "operator",
        )
        val TOKEN_MODIFIERS = listOf("definition")

        val LEGEND = SemanticTokensLegend(TOKEN_TYPES, TOKEN_MODIFIERS)

        private const val T_KEYWORD  = 0
        private const val T_FUNCTION = 1
        private const val T_VARIABLE = 2
        private const val T_NUMBER   = 3
        private const val T_STRING   = 4
        private const val T_COMMENT  = 5
        private const val T_OPERATOR = 6

        private const val M_DEFINITION = 1

        private val KEYWORD_TYPES = setOf(
            TokenType.TO, TokenType.END,
            TokenType.IF, TokenType.IFELSE, TokenType.IFTRUE, TokenType.IFFALSE,
            TokenType.REPEAT, TokenType.MAKE, TokenType.LOCALMAKE,
            TokenType.OUTPUT, TokenType.STOP, TokenType.LOCAL,
            TokenType.FOR, TokenType.WHILE, TokenType.UNTIL, TokenType.FOREVER,
            TokenType.FOREACH, TokenType.APPLY, TokenType.RUN,
            TokenType.TEST, TokenType.NAME, TokenType.THING,
            TokenType.CATCH, TokenType.THROW,
        )

        private val OPERATOR_TYPES = setOf(
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH,
            TokenType.PERCENT, TokenType.EQ, TokenType.NEQ,
            TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE,
        )
    }

    fun provide(): SemanticTokens {
        val procNames = analysis.symbolTable.procedures.keys

        data class Entry(val line: Int, val col: Int, val len: Int, val type: Int, val mods: Int)

        val entries = mutableListOf<Entry>()
        var nextIsDefName = false

        for (tok in analysis.tokens) {
            if (tok.type == TokenType.EOF || tok.type == TokenType.ERROR) continue
            if (tok.type == TokenType.NEWLINE) { nextIsDefName = false; continue }

            val type: Int
            val mods: Int

            when (tok.type) {
                in KEYWORD_TYPES -> {
                    nextIsDefName = (tok.type == TokenType.TO)
                    type = T_KEYWORD; mods = 0
                }
                TokenType.BUILTIN -> {
                    type = T_KEYWORD; mods = 0
                }
                TokenType.IDENTIFIER -> {
                    if (nextIsDefName) {
                        nextIsDefName = false
                        type = T_FUNCTION; mods = M_DEFINITION
                    } else if (tok.value.uppercase() in procNames) {
                        type = T_FUNCTION; mods = 0
                    } else {
                        type = T_FUNCTION; mods = 0
                    }
                }
                TokenType.COLON_WORD  -> { type = T_VARIABLE; mods = 0 }
                TokenType.QUOTED_WORD -> { type = T_STRING;   mods = 0 }
                TokenType.NUMBER      -> { type = T_NUMBER;   mods = 0 }
                TokenType.COMMENT     -> { type = T_COMMENT;  mods = 0 }
                in OPERATOR_TYPES     -> { type = T_OPERATOR; mods = 0 }
                else -> continue
            }

            entries += Entry(tok.line, tok.col, tok.value.length, type, mods)
        }

        val data = mutableListOf<Int>()
        var lastLine = 0; var lastCol = 0
        for (e in entries) {
            val deltaLine = e.line - lastLine
            val deltaStart = if (deltaLine == 0) e.col - lastCol else e.col
            data += deltaLine; data += deltaStart; data += e.len; data += e.type; data += e.mods
            lastLine = e.line; lastCol = e.col
        }

        return SemanticTokens(data)
    }
}
