package com.logolsp.parser

class Lexer(private val source: String) {
    fun tokenize(): List<Token> {
        // TODO: implement
        return listOf(Token(TokenType.EOF, "", 0, 0))
    }
}
