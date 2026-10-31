@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.remotegpt.app

import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.remotegpt.app.ui.*
import dev.remotegpt.client.*

/** Uses production pickers with offline data, without creating remote sessions. */
@Composable internal fun PickerGallery(kind: String) {
    ConversationGallery()
    var open by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(if (kind == "picker-model-long") "option-22" else "main") }
    if (!open) return
    when (kind) {
        "picker-model", "picker-model-long", "picker-model-running" -> ModelPickerSheet(
            if (kind == "picker-model-long") (1..25).map { "option-$it" to "本地示例模型 $it" }
            else listOf("main" to "GPT-5.4", "mini" to "GPT-5.4 mini", "codex" to "GPT-5.3-Codex"),
            selected, kind == "picker-model-running", true, {}, { selected = it.orEmpty(); open = false }, { open = false })
        "picker-effort" -> EffortPickerSheet(listOf("low", "medium", "high", "xhigh"), "high", false, false,
            { open = false }, { open = false })
        "picker-hosts" -> HostSwitcherSheet(listOf(
            HostProfile(id = "main", name = "开发服务器", endpoint = SshEndpoint("example.invalid", username = "demo")),
            HostProfile(id = "build", name = "构建服务器", endpoint = SshEndpoint("example.invalid", username = "demo")),
            HostProfile(id = "lab", name = "实验环境", endpoint = SshEndpoint("example.invalid", username = "demo"))),
            "main", mapOf("main" to HostWorkspace(connection = ConnectionStatus.ONLINE),
                "build" to HostWorkspace(connection = ConnectionStatus.RECONNECTING)), { open = false }, { open = false })
        "picker-projects" -> ModalBottomSheet(onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ProjectPicker(listOf(ProjectProfile("/home/dev/ghub", "GHub"), ProjectProfile("/home/dev/site", "个人网站"),
                ProjectProfile("/home/dev/api", "API"), ProjectProfile("/srv/api", "API")), "/home/dev/ghub", { open = false }, { open = false })
        }
    }
}
