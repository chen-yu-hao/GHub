package dev.remotegpt.app

import kotlinx.coroutines.*

/** A short Activity recreation or picker visit must not close the user's terminal. */
internal class BackgroundRelease(private val scope: CoroutineScope, private val release: () -> Unit) {
    private var pending: Job? = null
    fun update(active: Boolean) {
        if (active) { pending?.cancel(); pending = null }
        else if (pending == null) pending = scope.launch { delay(30_000); release() }
    }
}
