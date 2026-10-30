package com.bernaferrari.bravedns.ui.compose.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

/** Firewall navigation is UID-based, but notes belong to individual packages sharing that UID. */
@Composable
internal fun AppNotesSection(uid: Int, fallback: AppInfo) {
    val snapshots by FirewallManager.appListFlow().collectAsStateWithLifecycle()
    val packages = snapshots.filter { it.uid == uid }.distinctBy { it.packageName }
        .ifEmpty { listOf(fallback) }.sortedBy { it.packageName }
    Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
        packages.forEach { app ->
            key(app.uid, app.packageName) {
                AppNotesEditor(app, title = if (packages.size > 1)
                    stringResource(R.string.port_notes_package, app.packageName) else stringResource(R.string.port_notes))
            }
        }
    }
}
