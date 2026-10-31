package dev.remotegpt.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Uses the user's system haptic setting; no custom vibration permission is needed. */
@Composable
internal fun rememberHaptic(): (Boolean) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { strong: Boolean ->
            view.performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
}
