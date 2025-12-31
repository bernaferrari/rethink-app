/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.dialog

import Logger
import Logger.LOG_TAG_UI
import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.WgHopAdapter
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.wireguard.Config
import org.koin.core.component.KoinComponent

class WgHopDialog(
    private var activity: Activity,
    themeID: Int,
    private val srcId: Int,
    private val hopables: List<Config>,
    private val selectedId: Int
) : Dialog(activity, themeID), KoinComponent {

    private lateinit var adapter: WgHopAdapter

    companion object {
        private const val TAG = "HopDlg"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val composeView = ComposeView(context)
        composeView.setContent {
            RethinkTheme {
                WgHopContent(onDismiss = { dismiss() })
            }
        }
        setContentView(composeView)
        setCancelable(false)
        init()
    }

    private fun init() {
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        Logger.v(LOG_TAG_UI, "$TAG; init called")
        adapter = WgHopAdapter(activity, srcId, hopables, selectedId)
    }

    @Composable
    private fun WgHopContent(onDismiss: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = context.getString(R.string.hop_add_remove_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    RecyclerView(ctx).apply {
                        layoutManager = LinearLayoutManager(ctx)
                        adapter = this@WgHopDialog.adapter
                    }
                }
            )
            Button(
                onClick = {
                    Logger.d(LOG_TAG_UI, "$TAG; dismiss hop dialog")
                    onDismiss()
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.End)
            ) {
                Text(text = context.getString(R.string.ada_noapp_dialog_positive))
            }
        }
    }
}
