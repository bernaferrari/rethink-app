/*
 * Copyright 2024 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.components

import androidx.compose.runtime.Composable
import com.celzero.bravedns.R
import com.celzero.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.celzero.bravedns.database.ConsoleLog
import com.celzero.bravedns.ui.compose.logs.RethinkConsoleLogItem
import com.celzero.bravedns.ui.compose.logs.RethinkConsoleLogRow
import com.celzero.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.celzero.bravedns.util.Utilities

@Composable
fun ConsoleLogRow(log: ConsoleLog, isDebug: Boolean = DEBUG) {
    val logLevel = log.message.firstOrNull() ?: 'V'
    val timestamp =
        if (isDebug) {
            "${log.id}\n${Utilities.convertLongToTime(log.timestamp, TIME_FORMAT_1)}"
        } else {
            Utilities.convertLongToTime(log.timestamp, TIME_FORMAT_1)
        }

    RethinkConsoleLogRow(
        RethinkConsoleLogItem(level = logLevel, timestamp = timestamp, message = log.message),
    )
}
