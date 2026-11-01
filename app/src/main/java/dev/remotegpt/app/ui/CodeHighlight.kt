package dev.remotegpt.app.ui

/** Small, bounded lexical highlighting, never a formatter or shell parser. */
internal enum class CodeTone { KEYWORD, STRING, NUMBER, COMMENT, VARIABLE, COMMAND, OPTION, OPERATOR, ADD, REMOVE, META }
internal data class CodeToken(val start: Int, val end: Int, val tone: CodeTone)
internal data class HighlightedCode(val text: String, val tokens: List<CodeToken>)
internal data class CodeSlice(val start: Int, val end: Int)

// Bound both layout work and token allocation, even for a single enormous line.
internal fun codeSlice(text: String, start: Int = 0, maxLines: Int = 12, maxChars: Int = 4_000): CodeSlice {
    var end = start
    var lines = 0
    val limit = (start.toLong() + maxChars).coerceAtMost(text.length.toLong()).toInt()
    while (end < limit) {
        val c = text[end++]
        if (c == '\n' && ++lines >= maxLines) break
    }
    if (end < text.length && end > start && text[end - 1].isHighSurrogate()) end--
    return CodeSlice(start, end)
}

internal fun codePages(text: String): List<CodeSlice> = buildList {
    var start = 0
    while (start < text.length) {
        val page = codeSlice(text, start, maxLines = 80, maxChars = 8_000)
        add(page)
        start = page.end
    }
}

private val shellNames = setOf("sh", "bash", "zsh", "shell", "shellscript")
private val languages = shellNames + setOf("json", "jsonc", "python", "py", "javascript", "js", "typescript", "ts", "jsx", "tsx", "kotlin", "kt", "java", "yaml", "yml", "diff", "patch")
private val keywords = ("if else elif then fi for while do done in case esac function select until return break continue " +
    "def class import from as try except finally raise with lambda pass yield async await True False None " +
    "fun val var when is object interface package public private protected internal override open data sealed " +
    "const let export default new this super static void int boolean null true false throw throws switch catch typeof instanceof").split(' ').toSet()

internal fun highlightCode(original: String, language: String): HighlightedCode {
    val lang = language.trim().substringBefore(' ').lowercase()
    if (lang == "output") return ansiOutput(original)
    if (lang !in languages) return HighlightedCode(original, emptyList())
    val source = original.take(32_768)
    val tokens = ArrayList<CodeToken>()
    val limit = minOf(source.length, 32_768)
    fun token(start: Int, end: Int, tone: CodeTone) { if (start < end && tokens.size < 4_096) tokens.add(CodeToken(start, end, tone)) }
    if (lang in setOf("diff", "patch")) {
        var start = 0
        while (start < limit && tokens.size < 4_096) {
            val end = source.indexOf('\n', start).let { if (it < 0) limit else minOf(it, limit) }
            val tone = when {
                source.startsWith("+++", start) || source.startsWith("---", start) || source.startsWith("@@", start) || source.startsWith("diff ", start) -> CodeTone.META
                source[start] == '+' -> CodeTone.ADD
                source[start] == '-' -> CodeTone.REMOVE
                else -> null
            }
            if (tone != null) token(start, end, tone)
            start = end + 1
        }
        return HighlightedCode(original, tokens)
    }
    val shell = lang in shellNames
    val json = lang in setOf("json", "jsonc")
    val hashComments = shell || lang in setOf("python", "py", "yaml", "yml")
    var commandStart = true
    var i = 0
    while (i < limit && tokens.size < 4_096) {
        val start = i
        val c = source[i]
        when {
            c.isWhitespace() -> { if (c == '\n') commandStart = true; i++ }
            c == '\\' && shell -> { i = minOf(i + 2, limit) }
            (c == '#' && hashComments && (!shell || i == 0 || source[i - 1].isWhitespace())) ||
                (!shell && !hashComments && !json && source.startsWith("//", i)) ||
                (lang == "jsonc" && source.startsWith("//", i)) -> {
                i = source.indexOf('\n', i).let { if (it < 0) limit else minOf(it, limit) }
                token(start, i, CodeTone.COMMENT)
            }
            !shell && !hashComments && source.startsWith("/*", i) -> {
                i = source.indexOf("*/", i + 2).let { if (it < 0) limit else minOf(it + 2, limit) }
                token(start, i, CodeTone.COMMENT)
            }
            c == '\'' || c == '"' || (!json && c == '`') -> {
                val triple = !shell && source.startsWith(c.toString().repeat(3), i)
                val delimiter = c.toString().repeat(if (triple) 3 else 1)
                i += delimiter.length
                while (i < limit) {
                    if (source[i] == '\\' && !(shell && c == '\'')) { i = minOf(i + 2, limit); continue }
                    if (source.startsWith(delimiter, i)) { i = minOf(i + delimiter.length, limit); break }
                    i++
                }
                token(start, i, CodeTone.STRING)
                if (shell) commandStart = false
            }
            c == '$' && !json -> {
                i++
                if (i < limit && source[i] == '{') {
                    i = source.indexOf('}', i).let { if (it < 0) limit else minOf(it + 1, limit) }
                } else while (i < limit && (source[i].isLetterOrDigit() || source[i] in "_?@#!*-")) i++
                token(start, i, CodeTone.VARIABLE)
            }
            shell && c == '-' && i + 1 < limit && (source[i + 1].isLetter() || source[i + 1] == '-') -> {
                i++
                while (i < limit && (source[i].isLetterOrDigit() || source[i] in "_-")) i++
                token(start, i, CodeTone.OPTION)
            }
            c.isDigit() && (i == 0 || !source[i - 1].isLetterOrDigit()) -> {
                i++
                while (i < limit && (source[i].isDigit() || source[i] in ".xabcdefABCDEF")) i++
                token(start, i, CodeTone.NUMBER)
            }
            c.isLetter() || c == '_' || (shell && c in "./~") -> {
                i++
                while (i < limit && (source[i].isLetterOrDigit() || source[i] == '_' || (shell && source[i] in "./~-"))) i++
                val word = source.substring(start, i)
                val assignment = shell && i < limit && source[i] == '='
                val tone = when {
                    word in keywords && (!json || word in setOf("true", "false", "null")) -> CodeTone.KEYWORD
                    assignment -> CodeTone.VARIABLE
                    shell && commandStart -> CodeTone.COMMAND
                    else -> null
                }
                if (tone != null) token(start, i, tone)
                if (shell && !assignment) commandStart = word in setOf("then", "do", "else", "elif", "if", "while", "until", "sudo", "env")
            }
            c in "|&;()<>={}[]" -> {
                i++
                token(start, i, CodeTone.OPERATOR)
                if (shell && c in "|&;(") commandStart = true
            }
            else -> i++
        }
    }
    return HighlightedCode(original, tokens)
}

/** Read common SGR colors; ignore terminal controls rather than printing escapes.
 * These are log records, not a terminal emulator. Copy always uses the source. */
private fun ansiOutput(source: String): HighlightedCode {
    val text = StringBuilder(source.length)
    val tokens = ArrayList<CodeToken>()
    var tone: CodeTone? = null
    var run = 0
    var i = 0
    fun flush() { tone?.let { if (text.length > run && tokens.size < 4_096) tokens.add(CodeToken(run, text.length, it)) }; run = text.length }
    while (i < source.length) {
        val c = source[i++]
        if (c == '\u001b') {
            if (i >= source.length) break
            when (source[i++]) {
                '[' -> {
                    val start = i
                    while (i < source.length && source[i] !in '@'..'~') i++
                    if (i < source.length && source[i++] == 'm' && i - start <= 256) {
                        flush()
                        val codes = source.substring(start, i - 1).split(';').map { it.toIntOrNull() ?: 0 }
                        var n = 0
                        while (n < codes.size) {
                            when (val code = codes[n++]) {
                                0, 39 -> tone = null
                                30, 90, 37, 97 -> tone = CodeTone.COMMENT
                                31, 91 -> tone = CodeTone.REMOVE
                                32, 92 -> tone = CodeTone.ADD
                                33, 93 -> tone = CodeTone.NUMBER
                                34, 94, 36, 96 -> tone = CodeTone.COMMAND
                                35, 95 -> tone = CodeTone.KEYWORD
                                38, 48, 58 -> {
                                    // Extended colors are consumed as one unit, not confused with SGR codes.
                                    if (code == 38) tone = null
                                    val mode = codes.getOrNull(n++)
                                    n += if (mode == 2) 3 else if (mode == 5) 1 else 0
                                }
                            }
                        }
                    }
                }
                ']' -> {
                    while (i < source.length && source[i] != '\u0007' && !(source[i] == '\u001b' && source.getOrNull(i + 1) == '\\')) i++
                    if (i < source.length) i += if (source[i] == '\u0007') 1 else 2
                }
            }
        } else if (c == '\n' || c == '\t' || c >= ' ' && c != '\u007f') text.append(c)
    }
    flush()
    return HighlightedCode(text.toString(), tokens)
}
