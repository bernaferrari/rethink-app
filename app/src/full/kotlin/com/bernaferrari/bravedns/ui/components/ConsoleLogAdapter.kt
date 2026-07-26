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
package com.bernaferrari.bravedns.ui.components

import androidx.compose.runtime.Composable
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.RethinkDnsApplication.Companion.DEBUG
import com.bernaferrari.bravedns.database.ConsoleLog
import com.bernaferrari.bravedns.ui.compose.logs.RethinkConsoleLogItem
import com.bernaferrari.bravedns.ui.compose.logs.RethinkConsoleLogRow
import com.bernaferrari.bravedns.util.Constants.Companion.TIME_FORMAT_1
import com.bernaferrari.bravedns.util.Utilities

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
