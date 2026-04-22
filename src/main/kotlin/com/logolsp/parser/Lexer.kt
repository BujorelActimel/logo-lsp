package com.logolsp.parser

class Lexer(private val source: String) {
    private var pos  = 0
    private var line = 0
    private var col  = 0

    fun tokenize(): List<Token> {
        val result = mutableListOf<Token>()
        while (pos < source.length) {
            skipHorizontalWhitespace()
            if (pos >= source.length) break

            when (val c = source[pos]) {
                '\r' -> advance()
                '\n' -> result.add(newlineToken())
                '~'  -> lineContinuation()
                ';'  -> result.add(commentToken())
                '+'  -> result.add(single(TokenType.PLUS))
                '-'  -> result.add(single(TokenType.MINUS))
                '*'  -> result.add(single(TokenType.STAR))
                '/'  -> result.add(single(TokenType.SLASH))
                '%'  -> result.add(single(TokenType.PERCENT))
                '='  -> result.add(single(TokenType.EQ))
                '<'  -> result.add(ltOrNe())
                '>'  -> result.add(gtOrGte())
                '('  -> result.add(single(TokenType.LPAREN))
                ')'  -> result.add(single(TokenType.RPAREN))
                '['  -> result.add(single(TokenType.LBRACKET))
                ']'  -> result.add(single(TokenType.RBRACKET))
                '{'  -> result.add(single(TokenType.LBRACE))
                '}'  -> result.add(single(TokenType.RBRACE))
                ':'  -> result.add(colonWord())
                '"'  -> result.add(quotedWord())
                '|'  -> result.add(barWord())
                else -> when {
                    c.isDigit() || (c == '.' && nextIsDigit()) ->
                        result.add(number())
                    isWordStart(c) ->
                        result.add(word())
                    else -> {
                        result.add(Token(TokenType.ERROR, c.toString(), line, col))
                        advance()
                    }
                }
            }
        }
        result.add(Token(TokenType.EOF, "", line, col))
        return result
    }

    // Character classification

    /** Characters that can BEGIN an identifier (word). */
    private fun isWordStart(c: Char) = c.isLetter() || c in "_?#^!&@"

    /**
     * Characters that can CONTINUE an identifier after the first character.
     * Includes `.` for dotted names (find.win), `?!&@^#,'` for UCBLogo special procs
     * and words appearing inside list literals (e.g. [Hello, world!] or [Can't]).
     */
    private fun isWordContinue(c: Char) = c.isLetterOrDigit() || c in "_?.!&@^#,'"

    /** Terminates a `"word` quoted token. */
    private fun isQuotedWordEnd(c: Char) = c.isWhitespace() || c in "[](){};"

    private fun nextIsDigit() = pos + 1 < source.length && source[pos + 1].isDigit()

    // Cursor management

    private fun advance(): Char {
        val c = source[pos++]
        if (c == '\n') { line++; col = 0 } else col++
        return c
    }

    private fun peek(): Char? = source.getOrNull(pos)

    private fun skipHorizontalWhitespace() {
        while (pos < source.length && (source[pos] == ' ' || source[pos] == '\t')) advance()
    }

    /** `~` at end of line: skip the tilde, any trailing spaces, and the newline. */
    private fun lineContinuation() {
        advance() // consume '~'
        while (pos < source.length && source[pos] != '\n') advance()
        if (pos < source.length) advance() // consume '\n'
    }

    // Token builders

    private fun newlineToken(): Token {
        val t = Token(TokenType.NEWLINE, "\n", line, col)
        advance()
        return t
    }

    private fun commentToken(): Token {
        val startLine = line; val startCol = col
        val sb = StringBuilder()
        while (pos < source.length && source[pos] != '\n') sb.append(advance())
        return Token(TokenType.COMMENT, sb.toString(), startLine, startCol)
    }

    private fun single(type: TokenType): Token {
        val t = Token(type, source[pos].toString(), line, col)
        advance()
        return t
    }

    private fun ltOrNe(): Token {
        val sl = line; val sc = col
        advance() // consume '<'
        return when (peek()) {
            '=' -> { advance(); Token(TokenType.LTE, "<=", sl, sc) }
            '>' -> { advance(); Token(TokenType.NEQ, "<>", sl, sc) }
            else -> Token(TokenType.LT, "<", sl, sc)
        }
    }

    private fun gtOrGte(): Token {
        val sl = line; val sc = col
        advance() // consume '>'
        return if (peek() == '=') { advance(); Token(TokenType.GTE, ">=", sl, sc) }
               else Token(TokenType.GT, ">", sl, sc)
    }

    private fun colonWord(): Token {
        val sl = line; val sc = col
        val sb = StringBuilder(":")
        advance() // consume ':'
        while (pos < source.length && isWordContinue(source[pos])) sb.append(advance())
        return Token(TokenType.COLON_WORD, sb.toString(), sl, sc)
    }

    private fun quotedWord(): Token {
        val sl = line; val sc = col
        val sb = StringBuilder("\"")
        advance() // consume '"'
        if (peek() == '|') {
            // "| ... |"  vertical-bar quoted word (can contain spaces)
            sb.append(advance()) // consume '|'
            while (pos < source.length && source[pos] != '|') sb.append(advance())
            if (pos < source.length) sb.append(advance()) // consume closing '|'
        } else {
            while (pos < source.length && !isQuotedWordEnd(source[pos])) sb.append(advance())
        }
        return Token(TokenType.QUOTED_WORD, sb.toString(), sl, sc)
    }

    /** Standalone `| ... |` word (without a leading `"`). */
    private fun barWord(): Token {
        val sl = line; val sc = col
        val sb = StringBuilder("|")
        advance() // consume '|'
        while (pos < source.length && source[pos] != '|') sb.append(advance())
        if (pos < source.length) sb.append(advance()) // consume closing '|'
        return Token(TokenType.QUOTED_WORD, sb.toString(), sl, sc)
    }

    private fun number(): Token {
        val sl = line; val sc = col
        val sb = StringBuilder()
        while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) {
            sb.append(advance())
        }
        return Token(TokenType.NUMBER, sb.toString(), sl, sc)
    }

    private fun word(): Token {
        val sl = line; val sc = col
        val sb = StringBuilder()
        while (pos < source.length && isWordContinue(source[pos])) sb.append(advance())
        val raw   = sb.toString()
        val upper = raw.uppercase()
        val type  = KEYWORDS[upper] ?: if (upper in BUILTINS) TokenType.BUILTIN else TokenType.IDENTIFIER
        return Token(type, raw, sl, sc)
    }
}
