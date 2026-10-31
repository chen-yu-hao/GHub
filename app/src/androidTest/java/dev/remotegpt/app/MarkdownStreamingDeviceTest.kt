package dev.remotegpt.app

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import dev.remotegpt.app.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measures actual paragraph compositions, not an inferred frame-rate improvement. */
@RunWith(AndroidJUnit4::class)
class MarkdownStreamingDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unchangedParagraphsDoNotRecomposeOnEveryStreamingDelta() = runBlocking {
        val parser = ChatMarkdownParser()
        val prefix = (1..40).joinToString("\n\n", postfix = "\n\n") { "Paragraph $it: **中文** and `code`." }
        var document by mutableStateOf(parser.parse(prefix + "Tail"))
        var reuse by mutableStateOf(false)
        var paragraphs = 0
        val components = markdownComponents(paragraph = { model ->
            SideEffect { paragraphs++ }
            MarkdownParagraph(model.content, model.node)
        })
        compose.setContent {
            WorkbenchTheme {
                Markdown(state = document, components = components,
                    imageTransformer = remember { NoOpImageTransformerImpl() },
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    success = { parsed, parts, modifier ->
                        if (reuse) StableMarkdownContent(parsed, parts, modifier)
                        else Column(modifier) {
                            // Previous implementation: all nodes receive the growing full source.
                            parsed.node.children.forEach { node ->
                                key(node.type.toString(), node.startOffset) { MarkdownElement(node, parts, parsed.content) }
                            }
                        }
                    })
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { paragraphs = 0 }
        repeat(10) { step ->
            val next = parser.parse(prefix + "Tail " + "word ".repeat(step + 1))
            compose.runOnIdle { document = next }
            compose.waitForIdle()
        }
        var baseline = 0
        compose.runOnIdle { baseline = paragraphs; reuse = true }
        compose.waitForIdle()
        compose.runOnIdle { paragraphs = 0 }
        repeat(10) { step ->
            val next = parser.parse(prefix + "Tail " + "word ".repeat(step + 20))
            compose.runOnIdle { document = next }
            compose.waitForIdle()
        }
        compose.runOnIdle {
            Log.i("MarkdownStreaming", "40 fixed paragraphs, 10 updates: before=$baseline after=$paragraphs")
            assertTrue("Baseline should render the unchanged paragraphs", baseline >= 400)
            assertTrue("Only the changing tail should recompose; actual=$paragraphs", paragraphs in 10..20)
        }
    }
}
