package dev.remotegpt.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.remotegpt.app.ui.HostDetailsScreen
import dev.remotegpt.app.ui.TerminalPane
import dev.remotegpt.app.ui.WorkbenchTheme
import dev.remotegpt.client.HostProfile
import dev.remotegpt.client.SshEndpoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A failed optional task service must neither hide SSH nor pretend it grants task access. */
@RunWith(AndroidJUnit4::class)
class SshAvailabilityDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun taskServiceFailureStillAllowsDisconnectingTheAuthenticatedSshConnection() {
        var disconnected = 0
        var retried = 0
        val profile = HostProfile(name = "OpenSSH", endpoint = SshEndpoint(host = "dev.example", username = "dev"))
        compose.setContent { WorkbenchTheme {
            HostDetailsScreen(profile, HostWorkspace(connection = ConnectionStatus.ERROR, sshConnected = true),
                onBack = {}, onEdit = {}, onConnect = { retried++ }, onDisconnect = { disconnected++ })
        } }
        compose.onNodeWithText(tr(R.string.ui_ssh_connected)).assertExists()
        compose.onNodeWithText(tr(R.string.ui_ssh_without_task_service)).assertExists()
        compose.onNodeWithText(tr(R.string.ui_disconnect)).performScrollTo().performClick()
        assertEquals(1, disconnected)
        assertEquals(0, retried)
        compose.onNodeWithText(tr(R.string.ui_retry_task_service)).performScrollTo().performClick()
        assertEquals(1, retried)
    }

    @Test fun terminalUsesSshAvailabilityInsteadOfTheTaskServiceState() {
        var sshConnected by mutableStateOf(true)
        var terminals = 0
        var connections = 0
        compose.setContent { WorkbenchTheme {
            TerminalPane(emptyList(), null, "project", "/project", ConnectionStatus.ERROR, false, TerminalPreferences(),
                onBack = {}, onWorkspace = {}, onSelect = {}, onNew = { terminals++ }, onClose = {},
                onConnect = { connections++ }, onFontSize = {}, onExtraKeys = {}, sshConnected = sshConnected)
        } }
        compose.onNodeWithText(tr(R.string.ui_new_terminal)).assertIsEnabled().performClick()
        assertEquals(1, terminals)
        assertEquals(0, connections)
        compose.runOnIdle { sshConnected = false }
        compose.onNodeWithText(tr(R.string.ui_connect)).assertIsEnabled().performClick()
        assertEquals(1, connections)
    }
}
