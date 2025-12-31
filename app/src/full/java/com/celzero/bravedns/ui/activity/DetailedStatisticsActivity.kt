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
package com.celzero.bravedns.ui.activity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.ui.compose.statistics.DetailedStatisticsScreen
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.viewmodel.DetailedStatisticsViewModel
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class DetailedStatisticsActivity : AppCompatActivity() {
    private val viewModel: DetailedStatisticsViewModel by viewModel()

    companion object {
        const val INTENT_TYPE = "STATISTICS_TYPE"
        const val INTENT_TIME_CATEGORY = "TIME_CATEGORY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val typeInt = intent.getIntExtra(INTENT_TYPE, SummaryStatisticsType.MOST_CONNECTED_APPS.tid)
        val tcInt = intent.getIntExtra(INTENT_TIME_CATEGORY, 0)
        val type = SummaryStatisticsType.getType(typeInt)
        val timeCategory = SummaryStatisticsViewModel.TimeCategory.fromValue(tcInt)
            ?: SummaryStatisticsViewModel.TimeCategory.ONE_HOUR

        setContent {
            RethinkTheme {
                DetailedStatisticsScreen(
                    viewModel = viewModel,
                    type = type,
                    timeCategory = timeCategory,
                    onBackClick = { finish() }
                )
            }
        }
    }
}

