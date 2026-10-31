package dev.remotegpt.app.ui

import com.mikepenz.markdown.model.State
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class ChatMarkdownParserTest {
    private fun State.Success.blocks() = (node as ChatMarkdownRoot).blocks

    @Test fun streamingReusesFinishedBlocksWithoutKeepingOldWholeAnswers() = runBlocking {
        val parser = ChatMarkdownParser()
        val prefix = (1..80).joinToString("\n\n", postfix = "\n\n") { "Paragraph $it with **bold** and `code`." }
        val first = parser.parse(prefix + "Tail")
        var reused = 0
        repeat(40) { step ->
            val next = parser.parse(prefix + "Tail " + "word ".repeat(step + 1))
            first.blocks().take(80).zip(next.blocks()).forEach { (before, after) ->
                assertSame(before, after)
                assertTrue(after.source.length < 80)
                reused++
            }
            assertSame(first.referenceLinkHandler, next.referenceLinkHandler)
        }
        assertEquals(3200, reused)
    }

    @Test fun lateReferenceDefinitionsAndSourceReplacementRemainCorrect() = runBlocking {
        val parser = ChatMarkdownParser()
        val first = parser.parse("[Read][docs]\n\nStreaming")
        val next = parser.parse("[Read][docs]\n\nStreaming\n\n[docs]: https://example.com")
        assertEquals("https://example.com", next.referenceLinkHandler.find("[DOCS]"))
        assertNotSame(first.referenceLinkHandler, next.referenceLinkHandler)
        assertFalse(next.blocks().any { it.node.type == MarkdownElementTypes.LINK_DEFINITION })
        val replaced = parser.parse("**Different answer**")
        assertEquals("", replaced.referenceLinkHandler.find("[docs]"))
        assertEquals("**Different answer**", replaced.blocks().single().source)
    }

    @Test fun incompleteTablesFencesListsAndCjkHaveExactBlockLocalOffsets() = runBlocking {
        val parser = ChatMarkdownParser()
        val source = """
            # 标题

            | 名称 | 值 |
            | --- | ---: |
            | **中文** | `code` |

            1. first
               - nested **strong**
            2. second

            > quoted [link](https://example.com)

            ```kotlin
            fun hello() {
                println("中文 😀")
            }
            ```
        """.trimIndent()
        // Every prefix is a possible streamed document, including half delimiters.
        for (end in 1..source.length) {
            val text = source.take(end)
            val parsed = parser.parse(text)
            val root = parsed.node as ChatMarkdownRoot
            root.blocks.forEach { block ->
                val original = root.children.first { it.startOffset == block.offset }
                checkTree(original, block.node, text, block.source)
            }
        }
    }

    private fun checkTree(full: ASTNode, local: ASTNode, text: String, source: String) {
        assertEquals(full.type, local.type)
        assertEquals(text.substring(full.startOffset, full.endOffset), source.substring(local.startOffset, local.endOffset))
        assertEquals(full.children.size, local.children.size)
        full.children.zip(local.children).forEach { (a, b) ->
            assertSame(local, b.parent)
            checkTree(a, b, text, source)
        }
    }
}
