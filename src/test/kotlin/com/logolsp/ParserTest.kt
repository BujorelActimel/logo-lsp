package com.logolsp

import com.logolsp.ast.*
import com.logolsp.parser.Lexer
import com.logolsp.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParserTest {
    private fun parse(source: String): List<LogoNode> {
        val tokens = Lexer(source).tokenize()
        return Parser(tokens).parse()
    }

    private fun parseOne(source: String): LogoNode = parse(source).first()

    // Procedure definitions

    @Test fun `parse no-param procedure`() {
        val node = parseOne("to hello\n  print [Hi]\nend")
        assertIs<ProcedureDef>(node)
        assertEquals("HELLO", node.name)
        assertEquals(0, node.params.size)
    }

    @Test fun `parse one-param procedure`() {
        val node = parseOne("to greet :name\n  print :name\nend")
        assertIs<ProcedureDef>(node)
        assertEquals("GREET", node.name)
        assertEquals(1, node.params.size)
        assertEquals("name", node.params[0].name)
    }

    @Test fun `parse two-param procedure`() {
        val node = parseOne("to add :a :b\n  output :a + :b\nend")
        assertIs<ProcedureDef>(node)
        assertEquals(2, node.params.size)
        assertEquals("a", node.params[0].name)
        assertEquals("b", node.params[1].name)
    }

    @Test fun `parse three-param procedure`() {
        val node = parseOne("to between :lo :val :hi\n  output and (:val >= :lo) (:val <= :hi)\nend")
        assertIs<ProcedureDef>(node)
        assertEquals(3, node.params.size)
    }

    @Test fun `procedure name is normalised to uppercase`() {
        val node = parseOne("to Square :x\nend")
        assertIs<ProcedureDef>(node)
        assertEquals("SQUARE", node.name)
    }

    @Test fun `parse empty procedure body`() {
        val node = parseOne("to noop\nend")
        assertIs<ProcedureDef>(node)
        assertTrue(node.body.isEmpty())
    }

    @Test fun `parse multiple procedures`() {
        val nodes = parse("""
            to foo
            end
            to bar :x
            end
        """.trimIndent())
        assertEquals(2, nodes.size)
        assertIs<ProcedureDef>(nodes[0])
        assertIs<ProcedureDef>(nodes[1])
        assertEquals("FOO", (nodes[0] as ProcedureDef).name)
        assertEquals("BAR", (nodes[1] as ProcedureDef).name)
    }

    // MAKE / variable assignment

    @Test fun `parse MAKE statement`() {
        val node = parseOne("""make "x 42""")
        assertIs<MakeStmt>(node)
        assertEquals("x", node.varName)
    }

    @Test fun `parse MAKE with expression value`() {
        val node = parseOne("""make "total 3 + 4""")
        assertIs<MakeStmt>(node)
        assertEquals("total", node.varName)
    }

    // REPEAT

    @Test fun `parse REPEAT with block`() {
        val node = parseOne("repeat 4 [ fd 100 rt 90 ]")
        assertIs<RepeatStmt>(node)
        assertTrue(node.body.isNotEmpty())
    }

    @Test fun `parse nested REPEAT`() {
        val node = parseOne("repeat 3 [ repeat 3 [ fd 10 rt 90 ] ]")
        assertIs<RepeatStmt>(node)
        val inner = node.body.first()
        assertIs<RepeatStmt>(inner)
    }

    // IF / IFELSE

    @Test fun `parse IF statement`() {
        val node = parseOne("if :n = 0 [ stop ]")
        assertIs<IfStmt>(node)
        assertTrue(node.elseBranch == null)
    }

    @Test fun `parse IFELSE statement`() {
        val node = parseOne("ifelse :n > 0 [ print \"pos ] [ print \"neg ]")
        assertIs<IfStmt>(node)
        assertTrue(node.elseBranch != null)
    }

    // OUTPUT / STOP

    @Test fun `parse OUTPUT statement`() {
        val node = parseOne("output :x + 1")
        assertIs<OutputStmt>(node)
    }

    @Test fun `parse OP as alias for OUTPUT`() {
        val node = parseOne("op 42")
        assertIs<OutputStmt>(node)
    }

    @Test fun `parse STOP statement`() {
        val node = parseOne("stop")
        assertIs<StopStmt>(node)
    }

    // Expressions

    @Test fun `parse number literal`() {
        val node = parseOne("output 42")
        assertIs<OutputStmt>(node)
        assertIs<NumberLit>(node.value)
        assertEquals(42.0, (node.value as NumberLit).value)
    }

    @Test fun `parse variable reference`() {
        val node = parseOne("output :size")
        assertIs<OutputStmt>(node)
        val varRef = node.value
        assertIs<VarRef>(varRef)
        assertEquals("size", varRef.name)
    }

    @Test fun `parse quoted word`() {
        val node = parseOne("""output "hello""")
        assertIs<OutputStmt>(node)
        assertIs<WordLit>(node.value)
    }

    @Test fun `parse binary addition`() {
        val node = parseOne("output (3 + 4)")
        assertIs<OutputStmt>(node)
        val binOp = node.value
        assertIs<BinaryOp>(binOp)
        assertEquals("+", binOp.op)
    }

    // Procedure calls

    @Test fun `parse zero-arg procedure call`() {
        val nodes = parse("to foo\nend\nfoo")
        val call = nodes.last()
        assertIs<ProcedureCall>(call)
        assertEquals("FOO", call.name)
        assertEquals(0, call.args.size)
    }

    @Test fun `parse one-arg procedure call`() {
        val nodes = parse("to greet :x\nend\ngreet \"Alice")
        val call = nodes.last()
        assertIs<ProcedureCall>(call)
        assertEquals(1, call.args.size)
    }

    @Test fun `parse two-arg procedure call`() {
        val nodes = parse("to add :a :b\nend\nadd 3 4")
        val call = nodes.last()
        assertIs<ProcedureCall>(call)
        assertEquals(2, call.args.size)
    }

    // Range / position tracking

    @Test fun `procedure def range covers TO through END`() {
        val src = "to square :size\n  repeat 4 [fd :size rt 90]\nend"
        val node = parseOne(src)
        assertIs<ProcedureDef>(node)
        assertEquals(0, node.range.start.line)
        assertEquals(2, node.range.end.line)
    }

    @Test fun `param range is on the TO line`() {
        val node = parseOne("to square :size\nend")
        assertIs<ProcedureDef>(node)
        val param = node.params[0]
        assertEquals(0, param.range.start.line)
    }

    // Real corpus snippets

    @Test fun `parse tree procedure`() {
        val src = """
            to tree :size
              if :size < 5 [ forward :size back :size stop ]
              forward :size / 3
              left 30 tree :size * 2 / 3 right 30
              back :size
            end
        """.trimIndent()
        val node = parseOne(src)
        assertIs<ProcedureDef>(node)
        assertEquals("TREE", node.name)
        assertEquals(1, node.params.size)
    }

    @Test fun `parse poker hand evaluator`() {
        val src = javaClass.getResource("/logo/programs/poker.logo")!!.readText()
        val nodes = parse(src)
        val procs = nodes.filterIsInstance<ProcedureDef>()
        assertTrue(procs.size >= 10, "Expected at least 10 procedures, got ${procs.size}")
        val names = procs.map { it.name }.toSet()
        assertTrue("POKERHAND" in names)
        assertTrue("FOURP" in names)
        assertTrue("FLUSHP" in names)
    }

    @Test fun `parse pour puzzle solver`() {
        val src = javaClass.getResource("/logo/programs/pour.logo")!!.readText()
        val nodes = parse(src)
        val procs = nodes.filterIsInstance<ProcedureDef>()
        assertTrue(procs.size >= 5)
    }

    @Test fun `parse ttt game`() {
        val src = javaClass.getResource("/logo/programs/ttt.logo")!!.readText()
        val nodes = parse(src)
        val procs = nodes.filterIsInstance<ProcedureDef>()
        assertTrue(procs.size >= 15)
        val names = procs.map { it.name }.toSet()
        assertTrue("TTT" in names)
    }

    @Test fun `parse match pattern matcher`() {
        val src = javaClass.getResource("/logo/programs/match.logo")!!.readText()
        val nodes = parse(src)
        val procs = nodes.filterIsInstance<ProcedureDef>()
        assertTrue(procs.size >= 10)
    }
}
