/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R

/**
 * The short label for a log window ("1h", "24h", "7d"), so every window picker and subtitle reads
 * the same. Keyed on the `value` each view model's TimeCategory shares: 0, 1 and 2.
 */
@Composable
fun timeWindowLabel(timeCategoryValue: Int): String = stringResource(
    when (timeCategoryValue) {
        0 -> R.string.time_window_one_hour_short
        1 -> R.string.time_window_twenty_four_hours_short
        else -> R.string.time_window_seven_days_short
    },
)

/** "Last 24h": [timeWindowLabel] as a subtitle. */
@Composable
fun lastTimeWindowLabel(timeCategoryValue: Int): String =
    stringResource(R.string.two_argument_space, stringResource(R.string.lbl_last), timeWindowLabel(timeCategoryValue))
