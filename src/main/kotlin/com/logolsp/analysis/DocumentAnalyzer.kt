package com.logolsp.analysis

import com.logolsp.ast.LogoNode
import com.logolsp.parser.Lexer
import com.logolsp.parser.Parser
import com.logolsp.parser.Token

data class DocumentAnalysis(
    val uri: String,
    val source: String,
    val tokens: List<Token>,
    val ast: List<LogoNode>,
    val symbolTable: SymbolTable,
)

class DocumentAnalyzer {
    fun analyze(uri: String, source: String): DocumentAnalysis {
        // TODO: implement
        val tokens = Lexer(source).tokenize()
        val ast = Parser(tokens).parse()
        return DocumentAnalysis(uri, source, tokens, ast, SymbolTable())
    }
}
