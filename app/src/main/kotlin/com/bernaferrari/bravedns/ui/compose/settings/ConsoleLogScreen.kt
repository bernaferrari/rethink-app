/*
 * Copyright 2024 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.bernaferrari.bravedns.ui.compose.settings

import Logger
import Logger.LOG_TAG_BUG_REPORT
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.compose.collectAsLazyPagingItems
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.ConsoleLogRepository
import com.bernaferrari.bravedns.net.go.GoVpnAdapter
import com.bernaferrari.bravedns.service.PersistentState
import com.bernaferrari.bravedns.ui.compose.logs.ConsoleLogRow
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.viewmodel.ConsoleLogViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val pause by viewModel.pauseState.collectAsState()
    var clearFailed by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(pause.error) {
        pause.error?.let { Logger.e(LOG_TAG_BUG_REPORT, "Unable to freeze console logs", it) }
    }
    LaunchedEffect(viewModel) { viewModel.setLogLevel(Logger.uiLogLevel) }
    RethinkConsoleLogsScreen(
        strings = RethinkConsoleLogStrings(
            title = stringResource(R.string.console_log_title),
            pauseLabel = stringResource(R.string.console_pause_updates),
            resumeLabel = stringResource(R.string.console_resume_updates),
            pausedDescription = stringResource(R.string.console_paused_description, ConsoleLogViewModel.MAX_PAUSED_ROWS),
            pausingDescription = stringResource(R.string.console_pausing_description),
            clearingDescription = stringResource(R.string.console_clearing_description),
            searchHint = stringResource(R.string.lbl_search),
            filterDescription = stringResource(R.string.cd_filter),
            clearDescription = stringResource(R.string.console_log_delete_all_cd),
            moreDescription = stringResource(R.string.cd_more),
            clearSearchDescription = stringResource(R.string.cd_clear_search),
            deleteDialogTitle = stringResource(R.string.console_log_delete_dialog_title),
            deleteDialogMessage = stringResource(R.string.console_log_delete_dialog_desc),
            deleteLabel = stringResource(R.string.lbl_delete),
            reportLabel = stringResource(R.string.about_bug_report_desc),
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
        initialLogLevel = consoleLogSelectionIndex(Logger.uiLogLevel),
        clearBusy = clearing,
        paused = pause.paused,
        pauseBusy = pause.loading,
        pauseError = when {
            clearFailed -> stringResource(R.string.console_clear_failed)
            pause.error != null -> stringResource(R.string.console_pause_failed)
            else -> null
        },
        onPauseToggle = { viewModel.setPaused(!pause.paused) },
        onFilterChange = viewModel::setFilter,
        onLogLevelSelected = { selectedIndex ->
            val level = consoleLogSelectedLevel(selectedIndex)
            Logger.uiLogLevel = level
            GoVpnAdapter.setLogLevel(
                persistentState.goLoggerLevel.toInt(),
                Logger.uiLogLevel.toInt(),
                persistentState.includeFileTrace,
            )
            viewModel.setLogLevel(level)
            if (level < Logger.LoggerLevel.ERROR.id) {
                consoleLogRepository.setStartTimestamp(System.currentTimeMillis())
            }
            Logger.i(LOG_TAG_BUG_REPORT, "Log level set to $level")
        },
        onShareClick = onShareClick,
        onClearClick = {
            if (!clearing) {
                clearing = true
                clearFailed = false
                scope.launch {
                    try {
                        // Complete deletion and private-copy removal together even if this screen closes.
                        withContext(NonCancellable + Dispatchers.IO) {
                            consoleLogRepository.deleteAllLogs()
                            withContext(Dispatchers.Main) { viewModel.onLogsCleared() }
                        }
                        onDeleteComplete()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: androidx.sqlite.SQLiteException) {
                        Logger.e(LOG_TAG_BUG_REPORT, "Unable to delete console logs", error)
                        clearFailed = true
                    } catch (error: IllegalStateException) {
                        Logger.e(LOG_TAG_BUG_REPORT, "Console database unavailable", error)
                        clearFailed = true
                    } finally {
                        clearing = false
                    }
                }
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
