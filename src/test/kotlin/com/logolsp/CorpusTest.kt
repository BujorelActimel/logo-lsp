package com.logolsp

import com.logolsp.parser.Lexer
import com.logolsp.parser.TokenType
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertTrue

/**
 * Parses every .logo file in the test resources corpus and verifies
 * the lexer produces a valid (non-empty, EOF-terminated) token stream.
 */
class CorpusTest {
    @TestFactory
    fun `all corpus files lex without errors`(): List<DynamicTest> {
        val resourceRoot = javaClass.getResource("/logo")
            ?: error("Test resources directory /logo not found")
        val rootDir = File(resourceRoot.toURI())

        val logoFiles = rootDir.walkTopDown()
            .filter { it.isFile && it.extension == "logo" }
            .toList()
            .sortedBy { it.path }

        assertTrue(logoFiles.isNotEmpty(), "No .logo files found in test corpus")

        return logoFiles.map { file ->
            DynamicTest.dynamicTest(file.relativeTo(rootDir).path) {
                val source = file.readText()
                val tokens = Lexer(source).tokenize()

                // Must produce at least one token (EOF)
                assertTrue(tokens.isNotEmpty(), "Lexer produced no tokens for ${file.name}")

                // Last token must be EOF
                assertTrue(
                    tokens.last().type == TokenType.EOF,
                    "Last token is not EOF for ${file.name}: got ${tokens.last().type}"
                )

                // No ERROR tokens (may be relaxed later for specific edge-case files)
                val errorTokens = tokens.filter { it.type == TokenType.ERROR }
                assertTrue(
                    errorTokens.isEmpty(),
                    "Unexpected ERROR tokens in ${file.name}: $errorTokens"
                )
            }
        }
    }
}
