package dev.remotegpt.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.remotegpt.app.data.DraftRow
import dev.remotegpt.app.data.ThreadRow
import dev.remotegpt.client.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectVisibilityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun hiddenProjectSurvivesReloadWithoutDeletingSessionsOrDrafts() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RemoteApplication
        val vm = app.workbench()
        val store = ViewModelStore()
        val path = "/tmp" // A legitimate project here must remain until explicitly removed.
        val profile = HostProfile(name = "Local visibility test", endpoint = SshEndpoint("localhost", 1, "fixture"), defaultCwd = path)
        try {
            withContext(Dispatchers.Main) { vm.saveProfile(profile, emptyMap()) }.join()
            compose.waitUntil(10_000) { vm.state.value.profiles.any { it.id == profile.id } }
            withContext(Dispatchers.Main) { vm.selectHost(profile.id); vm.saveProject("Legitimate temporary project", path) }
            compose.waitUntil(10_000) { vm.state.value.workspace.projects.any { it.path == path } }
            val thread = ThreadSummary("visibility-fixture", "Stored session", path)
            app.database.workbench().putThreads(listOf(ThreadRow(profile.id, thread.id, wireJson.encodeToString(thread))))
            app.database.workbench().putDraft(DraftRow(profile.id, thread.id, wireJson.encodeToString(Draft(text = "Keep this draft"))))
            compose.onNodeWithContentDescription(app.getString(R.string.ui_actions_for_project, "Legitimate temporary project")).performScrollTo().performClick()
            compose.onNodeWithText(app.getString(R.string.ui_hide_project)).performClick()
            withTimeout(5_000) { while (path !in app.settings.hiddenProjects(profile.id)) delay(30) }
            assertFalse(vm.state.value.workspace.projects.any { it.path == path })
            assertNull(vm.state.value.workspace.selectedProject)
            val recovered = withContext(Dispatchers.Main) {
                ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[WorkbenchViewModel::class.java]
            }
            compose.waitUntil(10_000) { recovered.state.value.workspaces.containsKey(profile.id) }
            assertTrue(path in recovered.state.value.workspace.hiddenProjects)
            assertFalse(recovered.state.value.workspace.projects.any { it.path == path })
            assertEquals(thread.id, app.database.workbench().threads(profile.id).single().threadId)
            assertNotNull(app.database.workbench().draft(profile.id, thread.id))
            assertTrue(app.settings.hiddenProjects("another-host").isEmpty())
            withContext(Dispatchers.Main) { recovered.saveProject("Restored", path) }
            withTimeout(5_000) { while (path in app.settings.hiddenProjects(profile.id)) delay(30) }
            assertTrue(recovered.state.value.workspace.projects.any { it.path == path })
        } finally {
            withContext(Dispatchers.Main) { store.clear(); vm.deleteProfile(profile) }.join()
        }
    }
}
