package com.bernaferrari.bravedns.ui.compose.statistics

import Logger
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.service.VpnController
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkOverflowMenu
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols
import com.bernaferrari.bravedns.util.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CloseAllConnectionsAction() {
    val session by VpnController.sessionActive.collectAsState()
    val connection by VpnController.connectionStatus.collectAsState()
    val running = session && connection != null && VpnController.hasTunnel()
    val request = remember {
        CloseConnectionsRequest(VpnController::hasTunnel) {
            withContext(Dispatchers.IO) {
                // Check again on the dispatching thread; a displayed confirmation can outlive the VPN.
                if (VpnController.hasTunnel()) {
                    VpnController.closeConnectionsIfNeeded(Constants.UID_EVERYBODY, "statistics-manual-close")
                    true
                } else false
            }
        }
    }
    CloseAllConnectionsMenu(running, request)
}

@Composable
internal fun CloseAllConnectionsMenu(running: Boolean, request: CloseConnectionsRequest) {
    val scope = rememberCoroutineScope()
    val busy by request.busy.collectAsState()
    val dialog = remember { CloseConfirmationState() }
    val label = stringResource(R.string.stats_close_connections)
    RethinkOverflowMenu(
        moreDescription = stringResource(R.string.cd_more),
        items = listOf(RethinkOverflowItem(label, MaterialSymbols.Filled.Close, enabled = running && !busy) {
            dialog.show()
        }),
    )
    if (dialog.visible) RethinkConfirmDialog(
        title = label,
        message = when {
            !running || dialog.stopped -> stringResource(R.string.stats_close_connections_stopped)
            dialog.failed -> stringResource(R.string.stats_close_connections_failed)
            else -> stringResource(R.string.stats_close_connections_description)
        },
        confirmText = label,
        dismissText = stringResource(R.string.lbl_cancel),
        confirmEnabled = running && !dialog.stopped && !busy,
        dismissEnabled = !busy,
        isConfirmDestructive = true,
        onDismissRequest = { if (!busy) dialog.visible = false },
        onConfirm = {
            scope.launch { dialog.submit(request) }
        },
    )
}


private class CloseConfirmationState {
    var visible by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var stopped by mutableStateOf(false)

    fun show() {
        failed = false
        stopped = false
        visible = true
    }

    suspend fun submit(request: CloseConnectionsRequest) {
        try {
            when (request.confirm()) {
                CloseConnectionsRequest.Result.REQUESTED -> visible = false
                CloseConnectionsRequest.Result.STOPPED -> stopped = true
                CloseConnectionsRequest.Result.BUSY -> Unit
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IllegalStateException) {
            Logger.e(Logger.LOG_TAG_UI, "Unable to request connection close", error)
            failed = true
        }
    }
}
