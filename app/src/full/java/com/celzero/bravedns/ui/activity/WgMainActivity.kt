/*
 * Copyright 2023 RethinkDNS and its authors
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

import Logger
import Logger.LOG_TAG_PROXY
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.celzero.bravedns.R
import com.celzero.bravedns.data.AppConfig
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.ui.compose.wireguard.WgMainScreen
import com.celzero.bravedns.util.QrCodeFromFileScanner
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.TunnelImporter
import com.celzero.bravedns.util.Utilities
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.viewmodel.WgConfigViewModel
import com.google.zxing.qrcode.QRCodeReader
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class WgMainActivity : AppCompatActivity() {
    private val persistentState by inject<PersistentState>()
    private val appConfig by inject<AppConfig>()
    private val eventLogger by inject<EventLogger>()

    private val wgConfigViewModel: WgConfigViewModel by viewModel()

    companion object {
        private const val IMPORT_LAUNCH_INPUT = "*/*"
    }

    private val tunnelFileImportResultLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { data ->
            if (data == null) return@registerForActivityResult
            val contentResolver = contentResolver ?: return@registerForActivityResult
            lifecycleScope.launch {
                if (QrCodeFromFileScanner.validContentType(contentResolver, data)) {
                    try {
                        val qrCodeFromFileScanner =
                            QrCodeFromFileScanner(contentResolver, QRCodeReader())
                        val result = qrCodeFromFileScanner.scan(data)
                        Logger.i(LOG_TAG_PROXY, "result: $result, data: $data")
                        if (result != null) {
                            withContext(Dispatchers.Main) {
                                Logger.i(LOG_TAG_PROXY, "result: ${result.text}")
                                TunnelImporter.importTunnel(result.text) {
                                    Utilities.showToastUiCentered(
                                        this@WgMainActivity,
                                        it.toString(),
                                        Toast.LENGTH_LONG
                                    )
                                    Logger.e(LOG_TAG_PROXY, it.toString())
                                }
                                logEvent("Wireguard import", "imported from file")
                            }
                        } else {
                            val message =
                                resources.getString(
                                    R.string.generic_error,
                                    getString(R.string.invalid_file_error)
                                )
                            Utilities.showToastUiCentered(
                                this@WgMainActivity,
                                message,
                                Toast.LENGTH_LONG
                            )
                            Logger.e(LOG_TAG_PROXY, message)
                        }
                    } catch (e: Exception) {
                        val message =
                            resources.getString(
                                R.string.generic_error,
                                getString(R.string.invalid_file_error)
                            )
                        Utilities.showToastUiCentered(
                            this@WgMainActivity,
                            message,
                            Toast.LENGTH_LONG
                        )
                        Logger.e(LOG_TAG_PROXY, e.message ?: "err tun import", e)
                    }
                } else {
                    TunnelImporter.importTunnel(contentResolver, data) {
                        Logger.e(LOG_TAG_PROXY, it.toString())
                        Utilities.showToastUiCentered(
                            this@WgMainActivity,
                            it.toString(),
                            Toast.LENGTH_LONG
                        )
                    }
                }
            }
        }

    private val qrImportResultLauncher =
        registerForActivityResult(ScanContract()) { result ->
            val qrCode = result.contents
            if (qrCode != null) {
                lifecycleScope.launch {
                    TunnelImporter.importTunnel(qrCode) {
                        Utilities.showToastUiCentered(
                            this@WgMainActivity,
                            it.toString(),
                            Toast.LENGTH_LONG
                        )
                        Logger.e(LOG_TAG_PROXY, it.toString())
                        logEvent("Wireguard import", "imported via QR scanner")
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            RethinkTheme {
                WgMainScreen(
                    wgConfigViewModel = wgConfigViewModel,
                    persistentState = persistentState,
                    appConfig = appConfig,
                    eventLogger = eventLogger,
                    onBackClick = { finish() },
                    onCreateClick = { openTunnelEditorActivity() },
                    onImportClick = { launchFileImport() },
                    onQrScanClick = { launchQrScanner() }
                )
            }
        }
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun openTunnelEditorActivity() {
        val intent = Intent(this, WgConfigEditorActivity::class.java)
        startActivity(intent)
    }

    private fun launchFileImport() {
        try {
            tunnelFileImportResultLauncher.launch(IMPORT_LAUNCH_INPUT)
        } catch (e: ActivityNotFoundException) {
            Logger.e(LOG_TAG_PROXY, "err; anf; while launching file import: ${e.message}", e)
            Utilities.showToastUiCentered(
                this,
                getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "err while launching file import: ${e.message}", e)
            Utilities.showToastUiCentered(
                this,
                getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun launchQrScanner() {
        try {
            qrImportResultLauncher.launch(
                ScanOptions()
                    .setOrientationLocked(false)
                    .setBeepEnabled(false)
                    .setPrompt(resources.getString(R.string.lbl_qr_code))
            )
        } catch (e: ActivityNotFoundException) {
            Logger.e(LOG_TAG_PROXY, "err; anf while launching QR scanner: ${e.message}", e)
            Utilities.showToastUiCentered(
                this,
                getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
        } catch (e: Exception) {
            Logger.e(LOG_TAG_PROXY, "err while launching QR scanner: ${e.message}", e)
            Utilities.showToastUiCentered(
                this,
                getString(R.string.blocklist_update_check_failure),
                Toast.LENGTH_SHORT
            )
        }
    }

    private fun logEvent(msg: String, details: String) {
        eventLogger.log(EventType.PROXY_SWITCH, Severity.LOW, msg, EventSource.UI, false, details)
    }
}
