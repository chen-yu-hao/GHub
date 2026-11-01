package dev.remotegpt.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.remotegpt.app.ui.*
import dev.remotegpt.client.CommandExecutionDetails
import dev.remotegpt.client.TimelineItem

/** Offline, real components with sample execution data. */
@Composable internal fun CodeGallery() = Surface(color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("执行过程")
        CommandExecutionContent(TimelineItem("command", "preview", "commandExecution",
            "app/src/main/Connection.kt:42: reconnect()\napp/src/main/Session.kt:108: reconnect(host)\n\u001b[32mFound 2 matches\u001b[0m\n",
            true, executionStatus = "completed", commandExecution = CommandExecutionDetails(
                "rg --line-number 'reconnect|resume' app/src/main --glob '*.kt' | head -20", "/home/dev/ghub", 0, 186)))
        CodeBlock("# Keep the original indentation\ndef reconnect(host):\n    if host.online:\n        return {\"status\": \"ready\", \"retry\": 3}\n", "python", "Python")
        CodeBlock("{\n  \"host\": \"dev.example.com\",\n  \"connected\": true,\n  \"retries\": 3\n}", "json", "JSON")
        CommandExecutionContent(TimelineItem("failed", "preview", "commandExecution",
            "\u001b[31mFAILURE: Build failed with an exception.\u001b[0m\n" + (1..100).joinToString("\n") { "  build step $it: diagnostic details" },
            true, failed = true, executionStatus = "failed", commandExecution = CommandExecutionDetails("./gradlew :app:testDebugUnitTest", "/home/dev/ghub", 1, 2140)))
    }
}
