package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.scheduler.WorkScheduler
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.util.LogRetention
import org.koin.compose.koinInject

@Composable
internal fun LogRetentionSetting(persistentState: PersistentState) {
    val scheduler = koinInject<WorkScheduler>()
    var selected by remember { mutableStateOf(LogRetention.fromHours(persistentState.logRetentionHours)) }
    var showOptions by remember { mutableStateOf(false) }
    val title = stringResource(R.string.port_log_retention)
    RethinkListItem(
        headline = title,
        supporting = if (selected.hours < 24) androidx.compose.ui.res.pluralStringResource(R.plurals.port_hours, selected.hours, selected.hours)
            else androidx.compose.ui.res.pluralStringResource(R.plurals.port_days, selected.hours / 24, selected.hours / 24),
        position = CardPosition.Single,
        onClick = { showOptions = true },
    )
    val labels = LogRetention.entries.associateWith { option ->
        if (option.hours < 24) androidx.compose.ui.res.pluralStringResource(R.plurals.port_hours, option.hours, option.hours)
        else androidx.compose.ui.res.pluralStringResource(R.plurals.port_days, option.hours / 24, option.hours / 24)
    }
    if (showOptions) RethinkSelectionDialog(
        title = title,
        options = LogRetention.entries.map { RethinkSelectionOption(it.hours.toString(), labels.getValue(it)) },
        initialSelectedId = selected.hours.toString(),
        confirm = stringResource(R.string.port_save),
        cancel = stringResource(R.string.lbl_cancel),
        onDismiss = { showOptions = false },
        onConfirm = { option ->
            val retention = LogRetention.fromHours(option.id.toInt())
            persistentState.logRetentionHours = retention.hours
            scheduler.schedulePurgeConnectionsLog()
            selected = retention
            showOptions = false
        },
    )
}
