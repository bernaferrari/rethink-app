/*
 * Copyright 2020 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.celzero.bravedns.ui.compose.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.celzero.bravedns.R
import com.celzero.bravedns.ui.icons.MaterialSymbols

/** Android resource/action adapter for the shared single-screen welcome renderer. */
@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    RethinkWelcomeScreen(
        content = RethinkWelcomeContent(
            title = stringResource(R.string.app_name),
            description = stringResource(R.string.slide_2_desc),
            heroIcon = MaterialSymbols.Filled.Shield,
            features = listOf(
                RethinkWelcomeFeature(stringResource(R.string.firewall_mode_info_title), MaterialSymbols.Filled.Security),
                RethinkWelcomeFeature(stringResource(R.string.dns_mode_info_title), MaterialSymbols.Filled.Dns),
                RethinkWelcomeFeature(stringResource(R.string.lbl_wireguard), MaterialSymbols.Filled.VpnKey),
            ),
        ),
        ctaLabel = stringResource(R.string.finish),
        onFinish = onFinish,
    )
}
