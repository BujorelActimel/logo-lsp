package com.logolsp.parser

import com.logolsp.ast.*
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

/**
 * Recursive-descent parser for LOGO.
 *
 * Two-pass:
 *  1. Scan all TO headers to build a procedure arity table.
 *  2. Full parse, consulting the arity table to consume the correct number of
 *     arguments at each call site.
 */
class Parser(private val tokens: List<Token>) {
    private var pos = 0

    private val userArity = mutableMapOf<String, Int>()

    private val builtinArity: Map<String, Int> = buildMap {
        // turtle
        for (n in listOf("fd","forward","bk","back","rt","right","lt","left","arc",
                         "setx","sety","seth","setheading","setpensize",
                         "setpencolor","setpc","setbackground","setsc","setscreencolor",
                         "label","towards")) put(n, 1)
        for (n in listOf("setxy","setpos")) put(n, 1)
        for (n in listOf("home","pu","penup","pd","pendown","ppt","penpaint","pe","penerase",
                         "st","showturtle","ht","hideturtle","cs","clearscreen","clean",
                         "wrap","fence","window","ct","cleartext",
                         "fill","pi","repcount","heading","pos","xcor","ycor",
                         "pencolor","pc","pendownp","shownp")) put(n, 0)

        // math
        for (n in listOf("sqrt","sin","cos","tan","arcsin","arccos","arctan",
                         "abs","int","round","exp","ln","log10","minus",
                         "random","not","numberp","wordp","listp","emptyp",
                         "zerop","negativep","procedurep","primitivep","definedp",
                         "namep","macrop","first","last","butfirst","bf","butlast",
                         "bl","count","reverse","ascii","char","uppercase","lowercase",
                         "array","mdarray","arraytolist","listtoarray")) put(n, 1)
        for (n in listOf("sum","difference","product","quotient","remainder","modulo",
                         "power","max","min","and","or","xor","equalp","notequalp",
                         "lessp","greaterp","lessequalp","greaterequalp",
                         "fput","lput","word","list","sentence","se","item",
                         "memberp","member","remove","remdup","combine",
                         "push","pop","map","filter","reduce","find","map.se",
                         "foreach","apply","copydef","make","locate")) put(n, 2)
        for (n in listOf("setitem","mdsetitem","substring")) put(n, 3)
        put("cascade", 0)  // variadic – treat as 0 to avoid over-consuming

        // I/O
        for (n in listOf("print","pr","show","type","readword","readlist","rl",
                         "readchar","rc","readline","thing","name","arity",
                         "text","fulltext","erase","ignore","sort",
                         "localmake","local","global","run","runresult","invoke",
                         "throw","parse","unparse")) put(n, 1)
        for (n in listOf("error","pause","erall","pots","bye","wait",
                         "findregex","sortby")) put(n, 0)
        put("catch", 2)

        // control
        for (n in listOf("ifelse")) put(n, 3)   // condition thenList elseList
        for (n in listOf("test","iftrue","iff","iffalse","output","op","stop")) put(n, 1)
    }.mapKeys { it.key.uppercase() }

    private fun peek(): Token = tokens[pos]
    private fun peekType(): TokenType = tokens[pos].type
    private fun atEnd(): Boolean = peekType() == TokenType.EOF

    private fun advance(): Token {
        val t = tokens[pos]
        if (!atEnd()) pos++
        return t
    }

    private fun skipNewlines() {
        while (peekType() == TokenType.NEWLINE || peekType() == TokenType.COMMENT) advance()
    }

    private fun skipToNextStatement() {
        while (!atEnd() && peekType() != TokenType.NEWLINE) advance()
        skipNewlines()
    }

    private fun tokenRange(t: Token) = Range(
        Position(t.line, t.col),
        Position(t.line, t.endCol),
    )

    private fun rangeFrom(start: Token, endToken: Token) = Range(
        Position(start.line, start.col),
        Position(endToken.line, endToken.endCol),
    )

    private fun rangeFrom(start: Token, endPos: Position) = Range(
        Position(start.line, start.col),
        endPos,
    )

    private fun currentPos(): Position = peek().let { Position(it.line, it.col) }

    private fun lookupArity(name: String): Int {
        val upper = name.uppercase()
        return userArity[upper] ?: builtinArity[upper] ?: 0
    }

    private fun collectArities() {
        var i = 0
        while (i < tokens.size) {
            if (tokens[i].type == TokenType.TO) {
                i++
                while (i < tokens.size &&
                       (tokens[i].type == TokenType.NEWLINE || tokens[i].type == TokenType.COMMENT)) i++
                if (i >= tokens.size) break
                val name = tokens[i].value.uppercase()
                i++
                var arity = 0
                while (i < tokens.size && tokens[i].type == TokenType.COLON_WORD) { arity++; i++ }
                userArity[name] = arity
            } else {
                i++
            }
        }
    }

    fun parse(): List<LogoNode> {
        collectArities()
        pos = 0
        skipNewlines()
        val nodes = mutableListOf<LogoNode>()
        while (!atEnd()) {
            try {
                val node = parseStatement() ?: continue
                nodes += node
            } catch (_: Exception) {
                if (!atEnd()) skipToNextStatement()
            }
            skipNewlines()
        }
        return nodes
    }

    private fun parseStatement(): LogoNode? {
        skipNewlines()
        if (atEnd()) return null
        return when (peekType()) {
            TokenType.TO         -> parseProcedureDef()
            TokenType.MAKE,
            TokenType.LOCALMAKE  -> parseMake()
            TokenType.REPEAT     -> parseRepeat()
            TokenType.IF         -> parseIf(hasElse = false)
            TokenType.IFELSE     -> parseIf(hasElse = true)
            TokenType.OUTPUT     -> parseOutput()
            TokenType.STOP       -> parseStop()
            TokenType.FOR        -> parseFor()
            TokenType.WHILE      -> parseWhile()
            TokenType.UNTIL      -> parseUntil()
            TokenType.FOREVER    -> parseForever()
            TokenType.FOREACH,
            TokenType.APPLY      -> parseBuiltinCall()
            TokenType.LOCAL      -> { advance(); parseAtom(); null }
            TokenType.RUN        -> { val t = advance(); val arg = parseAtom()
                                      BuiltinCall("RUN", tokenRange(t), listOf(arg), rangeFrom(t, currentPos())) }
            TokenType.IFTRUE,
            TokenType.IFFALSE    -> parseIfTF()
            TokenType.TEST       -> parseTest()
            TokenType.NEWLINE,
            TokenType.COMMENT    -> { advance(); null }
            TokenType.BUILTIN    -> parseBuiltinCall()
            TokenType.IDENTIFIER -> parseUserCall(isStatement = true)
            else                 -> { advance(); null }
        }
    }

    private fun parseProcedureDef(): ProcedureDef {
        val toTok = advance()
        skipNewlines()
        val nameTok = advance()
        val name = nameTok.value.uppercase()
        val nameRange = tokenRange(nameTok)

        val params = mutableListOf<Param>()
        while (peekType() == TokenType.COLON_WORD) {
            val pt = advance()
            params += Param(pt.value.removePrefix(":"), tokenRange(pt))
        }

        while (!atEnd() && peekType() != TokenType.NEWLINE) advance()
        skipNewlines()

        val body = mutableListOf<LogoNode>()
        while (!atEnd() && peekType() != TokenType.END) {
            try {
                val s = parseStatement()
                if (s != null) body += s
            } catch (_: Exception) {
                if (!atEnd() && peekType() != TokenType.END) skipToNextStatement()
            }
            skipNewlines()
        }

        val endTok = if (peekType() == TokenType.END) advance() else peek()
        return ProcedureDef(
            name, nameRange, params, body,
            Range(Position(toTok.line, toTok.col), Position(endTok.line, endTok.endCol))
        )
    }

    private fun parseMake(): MakeStmt {
        val makeTok = advance()
        val nameTok = advance()
        val varName = nameTok.value.removePrefix("\"")
        val value = parseExpr()
        return MakeStmt(varName, tokenRange(nameTok), value, rangeFrom(makeTok, currentPos()))
    }

    private fun parseRepeat(): RepeatStmt {
        val t = advance()
        return RepeatStmt(parseAtom(), parseBlock(), rangeFrom(t, currentPos()))
    }

    private fun parseIf(hasElse: Boolean): IfStmt {
        val t = advance()
        val condition = parseCondition()
        val thenBranch = parseBlock()
        val elseBranch = if (hasElse || peekType() == TokenType.LBRACKET) parseBlock() else null
        return IfStmt(condition, thenBranch, elseBranch, rangeFrom(t, currentPos()))
    }

    private fun parseIfTF(): LogoNode {
        val t = advance()
        return IfStmt(WordLit("true", tokenRange(t)), parseBlock(), null, rangeFrom(t, currentPos()))
    }

    private fun parseTest(): LogoNode {
        val t = advance()
        val cond = parseExpr()
        return BuiltinCall("TEST", tokenRange(t), listOf(cond), rangeFrom(t, currentPos()))
    }

    private fun parseOutput(): OutputStmt {
        val t = advance()
        return OutputStmt(parseExpr(), rangeFrom(t, currentPos()))
    }

    private fun parseStop(): StopStmt {
        val t = advance()
        return StopStmt(tokenRange(t))
    }

    private fun parseFor(): ForStmt {
        val t = advance()
        // FOR [var start stop (step)] [body]
        val varName: String; val start: LogoNode; val stop: LogoNode; var step: LogoNode? = null
        if (peekType() == TokenType.LBRACKET) {
            advance()
            varName = if (peekType() == TokenType.IDENTIFIER) advance().value else "i"
            start = parseAtom(); stop = parseAtom()
            if (peekType() != TokenType.RBRACKET) step = parseAtom()
            if (peekType() == TokenType.RBRACKET) advance()
        } else {
            varName = "i"
            start = NumberLit(0.0, tokenRange(peek())); stop = NumberLit(0.0, tokenRange(peek()))
        }
        return ForStmt(varName, start, stop, step, parseBlock(), rangeFrom(t, currentPos()))
    }

    private fun parseWhile(): LogoNode {
        val t = advance()
        val condBlock = parseBlock()
        val body = parseBlock()
        return WhileStmt(BlockExpr(condBlock, rangeFrom(t, currentPos())), body, until = false, rangeFrom(t, currentPos()))
    }

    private fun parseUntil(): LogoNode {
        val t = advance()
        val condBlock = parseBlock()
        val body = parseBlock()
        return WhileStmt(BlockExpr(condBlock, rangeFrom(t, currentPos())), body, until = true, rangeFrom(t, currentPos()))
    }

    private fun parseForever(): LogoNode {
        val t = advance()
        return RepeatStmt(NumberLit(-1.0, tokenRange(t)), parseBlock(), rangeFrom(t, currentPos()))
    }

    private fun parseBuiltinCall(): BuiltinCall {
        val t = advance()
        val name = t.value.uppercase()
        val args = (1..(builtinArity[name] ?: 0)).map { parseAtom() }
        return BuiltinCall(name, tokenRange(t), args, rangeFrom(t, currentPos()))
    }

    private fun parseUserCall(isStatement: Boolean = false): ProcedureCall {
        val t = advance()
        val name = t.value.uppercase()
        val arity = lookupArity(name)
        val args = mutableListOf<LogoNode>()
        repeat(arity) {
            if (!atEnd() && peekType() !in STMT_STOP) args += parseAtom()
        }
        // In statement position, greedily capture any remaining scalar tokens on
        // the same line. This preserves args that pre-date a signature reduction
        // (e.g. `foo 1 2 3` after foo's arity changed from 3 to 2), so that
        // ChangeSignature can later compute the correct delta and trim them.
        // Restricted to statement position — doing this inside an expression
        // would consume tokens that belong to the outer call.
        if (isStatement) {
            while (!atEnd() && peek().line == t.line && peekType() in SCALAR_TOKENS) {
                args += parseAtom()
            }
        }
        return ProcedureCall(name, tokenRange(t), args, rangeFrom(t, currentPos()))
    }

    private fun parseCondition(): LogoNode {
        var left = parseAtom()
        while (peekType() in BINARY_OPS) {
            val op = advance()
            left = BinaryOp(op.value, left, parseAtom(), Range(left.range.start, currentPos()))
        }
        return left
    }

    private fun parseExpr(): LogoNode {
        var left = parseAtom()
        while (!atEnd() && peekType() in BINARY_OPS) {
            val op = advance()
            left = BinaryOp(op.value, left, parseAtom(), Range(left.range.start, currentPos()))
        }
        return left
    }

    private fun parseAtom(): LogoNode {
        skipNewlines()
        val t = peek()
        return when (t.type) {
            TokenType.NUMBER -> {
                advance(); NumberLit(t.value.toDoubleOrNull() ?: 0.0, tokenRange(t))
            }
            TokenType.MINUS -> {
                advance()
                val next = peek()
                if (next.type == TokenType.NUMBER) {
                    advance(); NumberLit(-(next.value.toDoubleOrNull() ?: 0.0), tokenRange(t))
                } else {
                    NumberLit(0.0, tokenRange(t))  // unary minus with non-literal operand
                }
            }
            TokenType.COLON_WORD -> {
                advance(); VarRef(t.value.removePrefix(":"), tokenRange(t))
            }
            TokenType.QUOTED_WORD -> {
                advance(); WordLit(t.value, tokenRange(t))
            }
            TokenType.LBRACKET -> {
                BlockExpr(parseBlock(), rangeFrom(t, currentPos()))
            }
            TokenType.LPAREN -> {
                advance()
                skipNewlines()
                val inner = parseParenExpr()
                if (peekType() == TokenType.RPAREN) advance()
                inner
            }
            TokenType.LBRACE -> {
                // array literal {1 2 3}
                val start = advance()
                val elems = mutableListOf<LogoNode>()
                while (!atEnd() && peekType() != TokenType.RBRACE) {
                    if (peekType() == TokenType.NEWLINE) { advance(); continue }
                    elems += parseAtom()
                }
                if (peekType() == TokenType.RBRACE) advance()
                BlockExpr(elems, rangeFrom(start, currentPos()))
            }
            TokenType.BUILTIN    -> parseBuiltinCall()
            TokenType.IDENTIFIER -> parseUserCall()
            else -> { advance(); WordLit(t.value, tokenRange(t)) }
        }
    }

    private fun parseParenExpr(): LogoNode {
        val t = peek()
        return when (t.type) {
            TokenType.IDENTIFIER -> {
                if (lookupArity(t.value) > 0) parseUserCall() else parseInfixChain()
            }
            TokenType.BUILTIN -> parseBuiltinCall()
            else -> parseInfixChain()
        }
    }

    private fun parseInfixChain(): LogoNode {
        var left = parseAtom()
        while (!atEnd() && peekType() in BINARY_OPS && peekType() != TokenType.RPAREN) {
            val op = advance()
            if (peekType() == TokenType.RPAREN) break
            left = BinaryOp(op.value, left, parseAtom(), Range(left.range.start, currentPos()))
        }
        return left
    }

    private fun parseBlock(): List<LogoNode> {
        if (peekType() != TokenType.LBRACKET) return emptyList()
        advance()
        val stmts = mutableListOf<LogoNode>()
        while (!atEnd() && peekType() != TokenType.RBRACKET) {
            if (peekType() == TokenType.NEWLINE || peekType() == TokenType.COMMENT) {
                advance(); continue
            }
            // TO and END are keywords outside blocks, but inside a list literal
            // they are just words (e.g. [Do you want to play first]).
            if (peekType() == TokenType.TO || peekType() == TokenType.END) {
                val kw = advance()
                stmts += WordLit(kw.value, tokenRange(kw))
                continue
            }
            try {
                val s = parseStatement()
                if (s != null) stmts += s
            } catch (_: Exception) {
                if (!atEnd() && peekType() != TokenType.RBRACKET) advance()
            }
        }
        if (peekType() == TokenType.RBRACKET) advance()
        return stmts
    }

    companion object {
        private val BINARY_OPS = setOf(
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH,
            TokenType.PERCENT, TokenType.EQ, TokenType.NEQ,
            TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE,
        )

        private val STMT_STOP = setOf(
            TokenType.NEWLINE, TokenType.EOF, TokenType.END,
            TokenType.RBRACKET, TokenType.RPAREN,
        )

        private val SCALAR_TOKENS = setOf(
            TokenType.NUMBER, TokenType.COLON_WORD, TokenType.QUOTED_WORD,
        )
    }
}
