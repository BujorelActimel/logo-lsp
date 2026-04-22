package com.logolsp

import com.logolsp.parser.Lexer
import com.logolsp.parser.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LexerTest {
    private fun lex(source: String) = Lexer(source).tokenize()
    private fun types(source: String) = lex(source).map { it.type }
    private fun values(source: String) = lex(source).map { it.value }

    // Keywords

    @Test fun `TO and END are keywords`() {
        val types = types("to square end")
        assertEquals(TokenType.TO,  types[0])
        assertEquals(TokenType.END, types[2])
    }

    @Test fun `keywords are case-insensitive`() {
        val cases = listOf("to", "TO", "To", "tO")
        for (kw in cases) {
            assertEquals(TokenType.TO, lex(kw)[0].type, "Failed for: $kw")
        }
    }

    @Test fun `IF IFELSE are keywords`() {
        val types = types("if ifelse")
        assertEquals(TokenType.IF,     types[0])
        assertEquals(TokenType.IFELSE, types[1])
    }

    @Test fun `REPEAT is a keyword`() {
        assertEquals(TokenType.REPEAT, lex("repeat")[0].type)
    }

    @Test fun `MAKE and LOCALMAKE are keywords`() {
        val types = types("make localmake")
        assertEquals(TokenType.MAKE,      types[0])
        assertEquals(TokenType.LOCALMAKE, types[1])
    }

    @Test fun `OUTPUT and OP are both keyword OUTPUT`() {
        assertEquals(TokenType.OUTPUT, lex("output")[0].type)
        assertEquals(TokenType.OUTPUT, lex("op")[0].type)
    }

    @Test fun `STOP is a keyword`() {
        assertEquals(TokenType.STOP, lex("stop")[0].type)
    }

    @Test fun `FOR WHILE UNTIL FOREVER are keywords`() {
        val types = types("for while until forever")
        assertEquals(TokenType.FOR,     types[0])
        assertEquals(TokenType.WHILE,   types[1])
        assertEquals(TokenType.UNTIL,   types[2])
        assertEquals(TokenType.FOREVER, types[3])
    }

    // Built-ins

    @Test fun `FD and FORWARD are builtins`() {
        assertEquals(TokenType.BUILTIN, lex("fd")[0].type)
        assertEquals(TokenType.BUILTIN, lex("forward")[0].type)
    }

    @Test fun `RT BK LT HOME are builtins`() {
        for (cmd in listOf("rt", "bk", "lt", "home", "right", "back", "left")) {
            assertEquals(TokenType.BUILTIN, lex(cmd)[0].type, "Failed for: $cmd")
        }
    }

    @Test fun `math builtins are recognised`() {
        for (cmd in listOf("sqrt", "sin", "cos", "abs", "round", "int", "pi")) {
            assertEquals(TokenType.BUILTIN, lex(cmd)[0].type, "Failed for: $cmd")
        }
    }

    @Test fun `list builtins are recognised`() {
        for (cmd in listOf("first", "last", "count", "fput", "lput", "butfirst", "bf")) {
            assertEquals(TokenType.BUILTIN, lex(cmd)[0].type, "Failed for: $cmd")
        }
    }

    // Colon-words (variable references)

    @Test fun `colon-word is a single token`() {
        val tokens = lex(":size")
        assertEquals(1, tokens.size - 1) // excluding EOF
        assertEquals(TokenType.COLON_WORD, tokens[0].type)
        assertEquals(":size", tokens[0].value)
    }

    @Test fun `colon-word in context`() {
        val tokens = lex("fd :size")
        assertEquals(TokenType.BUILTIN,    tokens[0].type)
        assertEquals(TokenType.COLON_WORD, tokens[1].type)
        assertEquals(":size",              tokens[1].value)
    }

    // Quoted words

    @Test fun `quoted word starts with double-quote`() {
        val tokens = lex(""""hello""")
        assertEquals(TokenType.QUOTED_WORD, tokens[0].type)
        assertEquals(""""hello""", tokens[0].value)
    }

    @Test fun `make uses quoted word for variable name`() {
        val types = types("""make "x 10""")
        assertEquals(TokenType.MAKE,        types[0])
        assertEquals(TokenType.QUOTED_WORD, types[1])
        assertEquals(TokenType.NUMBER,      types[2])
    }

    // Numbers

    @Test fun `integer literal`() {
        val tok = lex("42")[0]
        assertEquals(TokenType.NUMBER, tok.type)
        assertEquals("42", tok.value)
    }

    @Test fun `float literal`() {
        val tok = lex("3.14")[0]
        assertEquals(TokenType.NUMBER, tok.type)
        assertEquals("3.14", tok.value)
    }

    @Test fun `negative number after operator becomes minus + number`() {
        val types = types("fd -10")
        assertEquals(TokenType.BUILTIN, types[0])
        assertEquals(TokenType.MINUS,   types[1])
        assertEquals(TokenType.NUMBER,  types[2])
    }

    // Operators

    @Test fun `arithmetic operators`() {
        val types = types("+ - * / %")
        assertEquals(listOf(TokenType.PLUS, TokenType.MINUS, TokenType.STAR,
                            TokenType.SLASH, TokenType.PERCENT,
                            TokenType.EOF), types)
    }

    @Test fun `comparison operators`() {
        val types = types("= < > <= >= <>")
        assertEquals(TokenType.EQ,  types[0])
        assertEquals(TokenType.LT,  types[1])
        assertEquals(TokenType.GT,  types[2])
        assertEquals(TokenType.LTE, types[3])
        assertEquals(TokenType.GTE, types[4])
        assertEquals(TokenType.NEQ, types[5])
    }

    // Delimiters

    @Test fun `brackets and parens`() {
        val types = types("[ ] ( )")
        assertEquals(TokenType.LBRACKET, types[0])
        assertEquals(TokenType.RBRACKET, types[1])
        assertEquals(TokenType.LPAREN,   types[2])
        assertEquals(TokenType.RPAREN,   types[3])
    }

    // Comments

    @Test fun `semicolon starts a comment to end of line`() {
        val tokens = lex("; this is a comment\nfd 10")
        // comment token should be present (or skipped — either is acceptable)
        // what matters: the next meaningful token is BUILTIN (fd), not comment content
        val nonEof = tokens.filter { it.type != TokenType.EOF && it.type != TokenType.COMMENT && it.type != TokenType.NEWLINE }
        assertEquals(TokenType.BUILTIN, nonEof[0].type)
        assertEquals("fd", nonEof[0].value.lowercase())
    }

    // Position tracking

    @Test fun `first token starts at line 0 col 0`() {
        val tok = lex("to")[0]
        assertEquals(0, tok.line)
        assertEquals(0, tok.col)
    }

    @Test fun `newline advances line counter`() {
        val tokens = lex("to\nend")
        val toTok  = tokens[0]
        // find END token
        val endTok = tokens.first { it.type == TokenType.END }
        assertEquals(0, toTok.line)
        assertEquals(1, endTok.line)
        assertEquals(0, endTok.col)
    }

    @Test fun `column is tracked within a line`() {
        val tokens = lex("fd 100")
        val fdTok  = tokens[0]
        val numTok = tokens[1]
        assertEquals(0, fdTok.col)
        assertEquals(3, numTok.col)
    }

    // Full procedure tokenization

    @Test fun `full procedure definition tokenizes correctly`() {
        val src = """
            to square :size
              repeat 4 [ fd :size rt 90 ]
            end
        """.trimIndent()
        val types = types(src)

        assertTrue(types.contains(TokenType.TO))
        assertTrue(types.contains(TokenType.IDENTIFIER))   // "square"
        assertTrue(types.contains(TokenType.COLON_WORD))   // ":size"
        assertTrue(types.contains(TokenType.REPEAT))
        assertTrue(types.contains(TokenType.NUMBER))        // 4, 90
        assertTrue(types.contains(TokenType.BUILTIN))       // fd, rt
        assertTrue(types.contains(TokenType.END))
        assertEquals(TokenType.EOF, types.last())
    }

    @Test fun `EOF is always the last token`() {
        for (src in listOf("", " ", "\n", "to end", "repeat 4 [fd 10 rt 90]")) {
            val tokens = lex(src)
            assertEquals(TokenType.EOF, tokens.last().type, "Failed for: $src")
        }
    }
}
