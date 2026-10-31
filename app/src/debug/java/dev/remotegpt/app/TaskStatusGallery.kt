package dev.remotegpt.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.remotegpt.app.ui.*
import dev.remotegpt.client.TimelineItem

/** Real state components with offline sample data; no remote operations. */
@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable internal fun TaskStatusGallery() = WorkbenchTheme {
    var selected by remember { mutableStateOf(TaskStatus.RUNNING) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("任务状态 · Task status", style = MaterialTheme.typography.titleLarge)
        TaskStatus.entries.forEach { TaskStatusLabel(it) }
        HorizontalDivider()
        TaskStatusLabel(selected)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { selected = TaskStatus.RUNNING }) { Text("运行") }
            OutlinedButton(onClick = { selected = TaskStatus.COMPLETED }) { Text("完成") }
            OutlinedButton(onClick = { selected = TaskStatus.FAILED }) { Text("失败") }
        }
        listOf("inProgress", "completed", "failed").forEach { status ->
            TimelineCard(TimelineItem("sample-$status", "preview", "commandExecution", "Sample command output",
                complete = status != "inProgress", failed = status == "failed", executionStatus = status), {})
        }
    }
}
