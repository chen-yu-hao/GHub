package dev.remotegpt.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.remotegpt.app.ui.RemoteImageViewer
import dev.remotegpt.app.ui.WorkbenchTheme
import org.junit.Rule
import org.junit.Test

class ImageAccessibilityDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun imageCanZoomAndResetUsingAccessibleButtons() {
        compose.setContent { WorkbenchTheme { RemoteImageViewer("/missing-test-image") {} } }
        compose.onNodeWithContentDescription(tr(R.string.ui_zoom_out)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(tr(R.string.ui_zoom_in)).performClick()
        compose.onNodeWithText(tr(R.string.ui_image_zoom_percent, 150)).assertIsDisplayed()
        compose.onNodeWithContentDescription(tr(R.string.ui_zoom_out)).assertIsEnabled()
        compose.onNodeWithContentDescription(tr(R.string.ui_reset_image_zoom)).performClick()
        compose.onNodeWithText(tr(R.string.ui_image_zoom_percent, 100)).assertIsDisplayed()
    }
}
