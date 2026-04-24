package com.logolsp

import com.logolsp.analysis.DocumentAnalyzer
import com.logolsp.features.ChangeSignatureProvider
import org.eclipse.lsp4j.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeSignatureTest {
    private val uri = "file:///test.logo"

    private fun analyze(source: String) = DocumentAnalyzer().analyze(uri, source)

    private fun provider(source: String) = ChangeSignatureProvider(analyze(source))

    // Code action availability

    @Test fun `code action offered on procedure definition`() {
        val src = """
            to square :size
              repeat 4 [fd :size rt 90]
            end
            square 50
        """.trimIndent()
        val pos = Position(0, 5) // cursor on "square" in TO line
        val actions = provider(src).codeActionsAt(uri, pos)
        assertTrue(actions.isNotEmpty(), "Expected code action on procedure def")
        assertTrue(actions.any { it.title.contains("caller", ignoreCase = true) })
    }

    @Test fun `no code action when cursor is not on a procedure def`() {
        val src = """
            to square :size
              repeat 4 [fd :size rt 90]
            end
            square 50
        """.trimIndent()
        val pos = Position(1, 5) // cursor inside body
        val actions = provider(src).codeActionsAt(uri, pos)
        // No action expected when cursor is on the body line (not the TO line)
        assertTrue(actions.none { it.title.contains("caller", ignoreCase = true) })
    }

    // Adding a parameter

    @Test fun `adding a param appends placeholder to all callers`() {
        val src = """
            to square :size
              repeat 4 [fd :size rt 90]
            end
            square 50
            square 100
        """.trimIndent()
        // Simulate: user edits to "to square :size :color" then invokes the action
        val editedSrc = """
            to square :size :color
              repeat 4 [fd :size rt 90]
            end
            square 50
            square 100
        """.trimIndent()
        val edit = provider(editedSrc).computeSyncEdit(uri, "SQUARE")
        assertNotNull(edit, "Expected a WorkspaceEdit")

        val changes = edit.changes[uri] ?: edit.documentChanges
            ?.filterIsInstance<TextDocumentEdit>()
            ?.flatMap { it.edits }
        assertNotNull(changes)
        assertEquals(2, changes.size, "Expected 2 edits (one per call site)")

        // Each edit should append " 0"
        for (change in changes) {
            assertTrue(
                (change as TextEdit).newText.contains("0"),
                "Expected placeholder '0' in edit: ${change.newText}"
            )
        }
    }

    @Test fun `adding two params appends two placeholders`() {
        val src = """
            to dot :x :y
              pu setxy :x :y pd fd 1
            end
            dot 10 20
            dot 30 40
        """.trimIndent()
        val editedSrc = """
            to dot :x :y :color :size
              pu setxy :x :y pd fd 1
            end
            dot 10 20
            dot 30 40
        """.trimIndent()
        val edit = provider(editedSrc).computeSyncEdit(uri, "DOT")
        assertNotNull(edit)
        val changes = edit.changes[uri]!!
        assertEquals(2, changes.size)
        for (change in changes) {
            val text = (change as TextEdit).newText
            assertEquals(2, text.trim().split("\\s+".toRegex()).size,
                "Expected 2 placeholders, got: $text")
        }
    }

    // Removing a parameter

    @Test fun `removing last param trims trailing arg at all callers`() {
        val src = """
            to box :width :height :style
              repeat 2 [fd :width rt 90 fd :height rt 90]
            end
            box 30 50 1
            box 40 60 2
        """.trimIndent()
        val editedSrc = """
            to box :width :height
              repeat 2 [fd :width rt 90 fd :height rt 90]
            end
            box 30 50 1
            box 40 60 2
        """.trimIndent()
        val edit = provider(editedSrc).computeSyncEdit(uri, "BOX")
        assertNotNull(edit)
        val changes = edit.changes[uri]!!
        assertEquals(2, changes.size)
        // Edits should remove the trailing arg (empty replacement)
        for (change in changes) {
            assertEquals("", (change as TextEdit).newText,
                "Expected empty replacement to remove arg")
        }
    }

    // No change needed

    @Test fun `no edits when all callers already match signature`() {
        val src = """
            to square :size
              repeat 4 [fd :size rt 90]
            end
            square 50
            square 100
        """.trimIndent()
        assertNull(provider(src).computeSyncEdit(uri, "SQUARE"))
    }

    // No callers

    @Test fun `procedure with no callers produces no edits`() {
        val src = """
            to unused :x :y
              output :x + :y
            end
        """.trimIndent()
        val editedSrc = """
            to unused :x :y :z
              output :x + :y
            end
        """.trimIndent()
        assertNull(provider(editedSrc).computeSyncEdit(uri, "UNUSED"))
    }

    // Recursive callers

    @Test fun `recursive callers are also updated`() {
        val src = """
            to shrink :size :levels
              if :levels = 0 [stop]
              repeat 4 [fd :size rt 90]
              shrink :size / 2 :levels - 1
            end
            shrink 128 5
        """.trimIndent()
        val editedSrc = """
            to shrink :size :levels :angle
              if :levels = 0 [stop]
              repeat 4 [fd :size rt 90]
              shrink :size / 2 :levels - 1
            end
            shrink 128 5
        """.trimIndent()
        val edit = provider(editedSrc).computeSyncEdit(uri, "SHRINK")
        assertNotNull(edit)
        val changes = edit.changes[uri]!!
        // Both the recursive call and the top-level call should be updated
        assertEquals(2, changes.size)
    }
}
