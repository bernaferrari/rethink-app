/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.celzero.bravedns.ui.compose.settings

import Logger
import Logger.LOG_TAG_BUG_REPORT
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import com.celzero.bravedns.R
import com.celzero.bravedns.database.ConsoleLogRepository
import com.celzero.bravedns.net.go.GoVpnAdapter
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.components.ConsoleLogRow
import com.celzero.bravedns.ui.compose.theme.SharedDimensions
import com.celzero.bravedns.viewmodel.ConsoleLogViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Android service and paging adapter for the target-neutral console-log renderer. */
@Composable
fun ConsoleLogScreen(
    viewModel: ConsoleLogViewModel,
    consoleLogRepository: ConsoleLogRepository,
    persistentState: PersistentState,
    onShareClick: () -> Unit,
    onDeleteComplete: () -> Unit,
    onBackClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.setLogLevel(Logger.uiLogLevel) }
    RethinkConsoleLogsScreen(
        strings = RethinkConsoleLogStrings(
            title = stringResource(R.string.console_log_title),
            searchHint = stringResource(R.string.lbl_search),
            filterDescription = stringResource(R.string.cd_filter),
            shareDescription = stringResource(R.string.about_bug_report_desc),
            clearDescription = stringResource(R.string.ada_delete_logs_dialog_title),
            reportLabel = stringResource(R.string.about_bug_report_desc),
            confirmLabel = stringResource(R.string.fapps_info_dialog_positive_btn),
            cancelLabel = stringResource(R.string.lbl_cancel),
            filterOptions = listOf(
                stringResource(R.string.settings_gologger_dialog_option_0),
                stringResource(R.string.settings_gologger_dialog_option_1),
                stringResource(R.string.settings_gologger_dialog_option_2),
                stringResource(R.string.settings_gologger_dialog_option_3),
                stringResource(R.string.settings_gologger_dialog_option_4),
                stringResource(R.string.settings_gologger_dialog_option_5),
                stringResource(R.string.settings_gologger_dialog_option_6),
                stringResource(R.string.settings_gologger_dialog_option_7),
            ),
        ),
        initialLogLevel = Logger.uiLogLevel.toInt(),
        onFilterChange = viewModel::setFilter,
        onLogLevelSelected = { level ->
            Logger.uiLogLevel = level.toLong()
            GoVpnAdapter.setLogLevel(
                persistentState.goLoggerLevel.toInt(),
                Logger.uiLogLevel.toInt(),
                persistentState.includeFileTrace,
            )
            viewModel.setLogLevel(level.toLong())
            if (level < Logger.LoggerLevel.ERROR.id) {
                consoleLogRepository.setStartTimestamp(System.currentTimeMillis())
            }
            Logger.i(LOG_TAG_BUG_REPORT, "Log level set to $level")
        },
        onShareClick = onShareClick,
        onClearClick = {
            scope.launch(Dispatchers.IO) {
                Logger.i(LOG_TAG_BUG_REPORT, "deleting all console logs")
                consoleLogRepository.deleteAllLogs()
                onDeleteComplete()
            }
        },
        onBackClick = onBackClick,
    ) {
        ConsoleLogList(viewModel)
    }
}

@Composable
private fun ConsoleLogList(viewModel: ConsoleLogViewModel) {
    val items = viewModel.logs.collectAsLazyPagingItems()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        contentPadding = PaddingValues(bottom = SharedDimensions.spacing3xl),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(SharedDimensions.spacingXs),
    ) {
        items(count = items.itemCount) { index ->
            val item = items[index] ?: return@items
            ConsoleLogRow(item)
        }
    }
}
