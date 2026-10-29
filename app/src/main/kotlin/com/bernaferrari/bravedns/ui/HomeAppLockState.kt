package com.bernaferrari.bravedns.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Authentication is process-local, never restored from saved UI state. */
@Composable
internal fun rememberAppUnlockState(authenticationRequired: () -> Boolean): MutableState<Boolean> {
    val unlocked = remember { mutableStateOf(false) }
    val currentCheck = rememberUpdatedState(authenticationRequired)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var recheckOnStart = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Do not re-lock on returning from the system credential prompt: that stop
                // occurred while locked. Only an already unlocked session needs rechecking.
                Lifecycle.Event.ON_STOP -> recheckOnStart = unlocked.value
                Lifecycle.Event.ON_START -> {
                    if (recheckOnStart && currentCheck.value()) unlocked.value = false
                    recheckOnStart = false
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return unlocked
}
