/*
 * Copyright 2025 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.service

import Logger
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.bernaferrari.bravedns.database.ConnectionTrackerRepository
import com.bernaferrari.bravedns.util.BubbleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Firewall bubble lifecycle: show/dismiss via [BubbleHelper] and observe blocked-conn counts.
 */
class BubbleController(
    private val context: Context,
    private val persistentState: PersistentState,
    private val connTrackRepository: ConnectionTrackerRepository,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "VpnService;"
    }

    private var blockedConnsCollectorJob: Job? = null
    private var bubbleObserverRegistered = false
    private var lastBlockedCount = -1

    @RequiresApi(Build.VERSION_CODES.Q)
    fun handleIfNeeded() {
        if (!persistentState.firewallBubbleEnabled) {
            Logger.w(TAG, "Bubble disabled by user")
            unobserveBlockedConns()
            BubbleHelper.dismissBubble(context)
            return
        }

        initializeBubble()
    }

    fun dismissIfEnabled() {
        if (persistentState.firewallBubbleEnabled) {
            BubbleHelper.dismissBubble(context)
        }
    }

    fun unobserveBlockedConns() {
        if (bubbleObserverRegistered) {
            blockedConnsCollectorJob?.cancel()
            blockedConnsCollectorJob = null
            bubbleObserverRegistered = false
        }
        lastBlockedCount = -1
        Logger.i(TAG, "Bubble observer removed")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun initializeBubble() {
        try {
            // Request bubble. Bubbles are always backed by a notification, but we suppress the
            // shade entry via BubbleMetadata#setSuppressNotification(true).
            val eligible = BubbleHelper.showBubble(context, persistentState)
            Logger.i(TAG, "Bubble notification posted (eligible=$eligible)")

            // If not eligible, do not install observers / update loops.
            // Do not post any fallback notification (bubble-only UX).
            if (!eligible) {
                unobserveBlockedConns()
                return
            }

            if (bubbleObserverRegistered) {
                blockedConnsCollectorJob?.cancel()
                blockedConnsCollectorJob = null
                bubbleObserverRegistered = false
            }
            blockedConnsCollectorJob = connTrackRepository.observeBlockedConnectionsCount()
                .onEach { handleBlockedConnectionsCount(it) }
                .launchIn(scope)
            bubbleObserverRegistered = true
        } catch (e: SecurityException) {
            Logger.e(TAG, "Bubble init failed: ${e.message}", e)
            persistentState.firewallBubbleEnabled = false
            unobserveBlockedConns()
        } catch (e: IllegalStateException) {
            Logger.e(TAG, "Bubble init failed: ${e.message}", e)
            persistentState.firewallBubbleEnabled = false
            unobserveBlockedConns()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun handleBlockedConnectionsCount(t: Int) {
        // If user disabled bubble (or it was auto-disabled), stop observing.
        if (!persistentState.firewallBubbleEnabled) {
            try {
                BubbleHelper.dismissBubble(context)
                removeBubbleObserver()
            } catch (e: SecurityException) {
                Logger.w(TAG, "err stopping bubble observer: ${e.message}", e)
            } catch (e: IllegalStateException) {
                Logger.w(TAG, "err stopping bubble observer: ${e.message}", e)
            }
            return
        }

        if (t != lastBlockedCount) {
            // Pass persistentState so it can be disabled if bubbles are not allowed
            BubbleHelper.updateBubble(context, t, persistentState)
            lastBlockedCount = t
            Logger.d(TAG, "Bubble updated: $t blocked")

            // updateBubble can flip the toggle off (permission revoked). If that happened,
            // stop observing and clean up.
            if (!persistentState.firewallBubbleEnabled) {
                BubbleHelper.dismissBubble(context)
                removeBubbleObserver()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun removeBubbleObserver() {
        if (bubbleObserverRegistered) {
            blockedConnsCollectorJob?.cancel()
            blockedConnsCollectorJob = null
            bubbleObserverRegistered = false
        }
        lastBlockedCount = -1
        Logger.i(TAG, "Bubble observer removed")
    }
}
