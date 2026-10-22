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
package com.bernaferrari.bravedns.ui.compose.statistics

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.bernaferrari.bravedns.data.SummaryStatisticsType
import com.bernaferrari.bravedns.ui.compose.common.rememberAppIconPainter

internal fun SummaryStatisticsType.supportsAppIcon(): Boolean {
    return this == SummaryStatisticsType.MOST_CONNECTED_APPS ||
        this == SummaryStatisticsType.MOST_BLOCKED_APPS
}

@Composable
internal fun rememberStatisticsAppIconPainter(uid: Int): Painter? = rememberAppIconPainter(uid)
