package dev.remotegpt.app.ui

import org.junit.Assert.*
import org.junit.Test

class CodeHighlightTest {
    @Test fun shellHighlightPreservesQuotesTabsUnicodeAndContinuations() {
        val source = "rg --line-number '中文 #string' \"\$HOME\" \\\n\tapp/src | head -20\n# comment"
        val result = highlightCode(source, "bash")
        assertEquals(source, result.text)
        fun has(value: String, tone: CodeTone) = result.tokens.any { source.substring(it.start, it.end) == value && it.tone == tone }
        assertTrue(has("rg", CodeTone.COMMAND))
        assertTrue(has("--line-number", CodeTone.OPTION))
        assertTrue(has("'中文 #string'", CodeTone.STRING))
        assertTrue(has("# comment", CodeTone.COMMENT))
        assertTrue(has("|", CodeTone.OPERATOR))
        assertTrue(has("head", CodeTone.COMMAND))
        assertFalse(result.tokens.any { it.tone == CodeTone.COMMENT && it.start < source.indexOf("# comment") })
    }

    @Test fun incompleteCodeAndUnknownLanguagesRemainReadableWithoutReformatting() {
        val partial = "{\n  \"key\": \"incomplete\\\""
        assertEquals(partial, highlightCode(partial, "json").text)
        assertTrue(highlightCode(partial, "json").tokens.any { it.tone == CodeTone.STRING && it.end == partial.length })
        val source = "\tunknown <syntax> # keep it\n"
        assertEquals(HighlightedCode(source, emptyList()), highlightCode(source, "unknown"))
        assertEquals(source, highlightCode(source, "text").text)
        val python = "def hello():\n    print(\"中文\")\n"
        assertEquals(python, highlightCode(python, "python").text)
        assertEquals(CodeTone.KEYWORD, highlightCode(python, "python").tokens.first().tone)
    }

    @Test fun logsConsumeTerminalControlsWithoutInterpretingTextAsCode() {
        val result = highlightCode("\u001b[31merror\u001b[0m plain\n\u001b]8;;https://example.com\u0007link\u001b]8;;\u0007\u001b[2K", "output")
        assertEquals("error plain\nlink", result.text)
        assertEquals(listOf(CodeToken(0, 5, CodeTone.REMOVE)), result.tokens)
        assertEquals("hello", highlightCode("hello\u001b[31", "output").text)
        assertEquals("red?", highlightCode("\u001b[38;2;31;32;33mred?", "output").text)
        assertTrue(highlightCode("\u001b[38;2;31;32;33mred?", "output").tokens.isEmpty())
    }

    @Test fun hugeSingleLinesAndManyLinesHaveBoundedPreviewsAndLosslessPages() {
        val source = "中😀\t".repeat(6000) + "\nrow\n".repeat(200)
        val excerpt = codeSlice(source)
        assertTrue(excerpt.end <= 4000)
        assertFalse(source[excerpt.end - 1].isHighSurrogate())
        val pages = codePages(source)
        assertEquals(source, pages.joinToString("") { source.substring(it.start, it.end) })
        assertTrue(pages.all { it.end - it.start <= 8000 })
        assertTrue(pages.zipWithNext().all { (a, b) -> a.end == b.start })
        val multiline = "a\n".repeat(1000)
        assertEquals(24, codeSlice(multiline).end)
        val highlighted = highlightCode("{}".repeat(100_000), "json")
        assertTrue(highlighted.tokens.size <= 4096)
        assertEquals(200_000, highlighted.text.length)
    }

    @Test fun diffFileHeadersAreNotMistakenForAddedOrRemovedLines() {
        val source = "--- a/file\n+++ b/file\n@@ -1 +1 @@\n-old\n+new\n"
        assertEquals(listOf(CodeTone.META, CodeTone.META, CodeTone.META, CodeTone.REMOVE, CodeTone.ADD), highlightCode(source, "diff").tokens.map { it.tone })
    }
}
