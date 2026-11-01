package dev.remotegpt.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.remotegpt.app.ui.HostDetailsScreen
import dev.remotegpt.app.ui.WorkbenchTheme
import dev.remotegpt.client.HostProfile
import dev.remotegpt.client.SshEndpoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostDetailsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionActionsStayInSettingsAndFollowTheConnectionState() {
        var workspace by mutableStateOf(HostWorkspace())
        var connections = 0
        var disconnections = 0
        var edits = 0
        val profile = HostProfile(name = "Development", endpoint = SshEndpoint(host = "dev.example", username = "dev"))
        compose.setContent { WorkbenchTheme {
            HostDetailsScreen(profile, workspace, {}, { edits++ },
                onConnect = { connections++; workspace = workspace.copy(connection = ConnectionStatus.CONNECTING) },
                onDisconnect = { disconnections++; workspace = workspace.copy(connection = ConnectionStatus.OFFLINE) })
        } }
        compose.onNodeWithText(tr(R.string.ui_connect)).performClick()
        compose.onNodeWithText(tr(R.string.ui_cancel_connection)).assertIsDisplayed().performClick()
        assertEquals(1, connections)
        assertEquals(1, disconnections)
        compose.onNodeWithText(tr(R.string.ui_connect)).assertIsDisplayed()

        compose.runOnIdle { workspace = workspace.copy(connection = ConnectionStatus.ONLINE, serverVersion = "0.165.0") }
        compose.onNodeWithText(tr(R.string.ui_open_projects)).assertDoesNotExist()
        compose.onAllNodesWithText(tr(R.string.ui_disconnect)).assertCountEquals(1)
        compose.onNodeWithText(tr(R.string.ui_edit_connection)).performScrollTo().performClick()
        assertEquals(1, edits)
        assertEquals(ConnectionStatus.ONLINE, workspace.connection)
        compose.onNodeWithText(tr(R.string.ui_disconnect)).performScrollTo().performClick()
        compose.onNodeWithText(tr(R.string.ui_remote_environment)).assertDoesNotExist()
        assertEquals(2, disconnections)

        compose.runOnIdle { workspace = workspace.copy(connection = ConnectionStatus.WAITING_NETWORK) }
        compose.onNodeWithText(tr(R.string.ui_stop_reconnecting)).performScrollTo().performClick()
        assertEquals(ConnectionStatus.OFFLINE, workspace.connection)
        assertEquals(3, disconnections)

        compose.runOnIdle { workspace = workspace.copy(connection = ConnectionStatus.ERROR, needsBootstrap = true) }
        compose.onNodeWithText(tr(R.string.ui_set_up_and_connect)).performScrollTo().performClick()
        assertEquals(2, connections)
        compose.onNodeWithText(tr(R.string.ui_cancel_connection)).assertIsDisplayed()
    }
}
