/*
 * Copyright 2022 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.celzero.bravedns.data.SummaryStatisticsType
import com.celzero.bravedns.ui.activity.DetailedStatisticsActivity
import com.celzero.bravedns.ui.compose.statistics.SummaryStatisticsScreen
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.viewmodel.SummaryStatisticsViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class SummaryStatisticsFragment : Fragment() {
    private val viewModel: SummaryStatisticsViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                RethinkTheme {
                    SummaryStatisticsScreen(
                        viewModel = viewModel,
                        onSeeMoreClick = { type ->
                            openDetailedStatsUi(type)
                        }
                    )
                }
            }
        }
    }

    private fun openDetailedStatsUi(type: SummaryStatisticsType) {
        val timeCategory = viewModel.uiState.value.timeCategory.value
        val intent = Intent(requireContext(), DetailedStatisticsActivity::class.java)
        intent.putExtra(DetailedStatisticsActivity.INTENT_TYPE, type.tid)
        intent.putExtra(DetailedStatisticsActivity.INTENT_TIME_CATEGORY, timeCategory)
        startActivity(intent)
    }

    companion object {
        fun newInstance() = SummaryStatisticsFragment()
    }
}

