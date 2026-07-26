/*
 * Copyright 2020 RethinkDNS developers
 *
 * Copyright 2019 Jigsaw Operations LLC
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
package com.bernaferrari.bravedns.net.go

import android.content.Context
import com.bernaferrari.bravedns.net.doh.Prober

/** Implements a probe using the Go-based DoH client. */
class GoProber(@Suppress("UNUSED_PARAMETER") context: Context) : Prober() {
    override fun probe(url: String, callback: Prober.Callback) {
        // The Go transport is intentionally disabled until the native client supplies probes.
    }
}
