/*
Copyright 2021 RethinkDNS and its authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.celzero.bravedns

import Logger
import Logger.LOG_TAG_APP_UPDATE
import android.app.Activity
import com.celzero.bravedns.network.HttpClientManager
import com.celzero.bravedns.service.AppUpdater
import com.celzero.bravedns.service.PersistentState
import com.celzero.bravedns.util.Constants.Companion.INIT_TIME_MS
import com.celzero.bravedns.util.Constants.Companion.JSON_LATEST
import com.celzero.bravedns.util.Constants.Companion.JSON_UPDATE
import com.celzero.bravedns.util.Constants.Companion.JSON_VERSION
import com.celzero.bravedns.util.Constants.Companion.UPDATE_CHECK_RESPONSE_VERSION
import com.celzero.bravedns.util.JsonHelper
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NonStoreAppUpdater(
    private val baseUrl: String,
    private val persistentState: PersistentState,
) : AppUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun checkForAppUpdate(
        isInteractive: AppUpdater.UserPresent,
        activity: Activity,
        listener: AppUpdater.InstallStateListener,
    ) {
        Logger.i(LOG_TAG_APP_UPDATE, "Beginning update check")
        val url = baseUrl + BuildConfig.VERSION_CODE
        val client = HttpClientManager.genericClient(persistentState.routeRethinkInRethink)

        scope.launch {
            try {
                val response = client.get(url)
                if (!response.status.isSuccess()) {
                    listener.onUpdateCheckFailed(AppUpdater.InstallSource.OTHER, isInteractive)
                    return@launch
                }

                val res = response.bodyAsText()
                if (res.isBlank()) {
                    listener.onUpdateCheckFailed(AppUpdater.InstallSource.OTHER, isInteractive)
                    return@launch
                }

                val json = JsonHelper.parseObject(res)
                val version = JsonHelper.getInt(json, JSON_VERSION)
                val shouldUpdate = JsonHelper.getBoolean(json, JSON_UPDATE)
                val latest = JsonHelper.getLong(json, JSON_LATEST, INIT_TIME_MS)
                persistentState.lastAppUpdateCheck = System.currentTimeMillis()

                Logger.i(
                    LOG_TAG_APP_UPDATE,
                    "Server response for the new version download is $shouldUpdate (json version: $version), version number: $latest",
                )

                if (version != UPDATE_CHECK_RESPONSE_VERSION) {
                    listener.onUpdateCheckFailed(AppUpdater.InstallSource.OTHER, isInteractive)
                    return@launch
                }

                if (!shouldUpdate) {
                    listener.onUpToDate(AppUpdater.InstallSource.OTHER, isInteractive)
                } else {
                    listener.onUpdateAvailable(AppUpdater.InstallSource.OTHER)
                }
            } catch (e: Exception) {
                listener.onUpdateCheckFailed(AppUpdater.InstallSource.OTHER, isInteractive)
            }
        }
    }

    override fun completeUpdate() {
        /* no-op */
    }

    override fun unregisterListener(listener: AppUpdater.InstallStateListener) {
        /* no-op */
    }
}