/*
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
package com.bernaferrari.bravedns.net.doh

import android.content.Context
import com.bernaferrari.bravedns.net.go.GoProber

/** Races DoH probes and reports the first successful endpoint, or -1 when all probes fail. */
class Race {
    fun interface Listener {
        fun onResult(index: Int)
    }

    private class Collector(
        private val numCallbacks: Int,
        private val listener: Listener,
    ) {
        private var numFailed = 0
        private var reportedSuccess = false

        @Synchronized
        fun onCompleted(index: Int, succeeded: Boolean) {
            if (succeeded) {
                if (!reportedSuccess) {
                    listener.onResult(index)
                    reportedSuccess = true
                }
            } else {
                numFailed++
                if (numFailed == numCallbacks) listener.onResult(-1)
            }
        }
    }

    private class Callback(
        private val index: Int,
        private val collector: Collector,
    ) : Prober.Callback {
        override fun onCompleted(succeeded: Boolean) = collector.onCompleted(index, succeeded)
    }

    companion object {
        @JvmStatic
        fun start(context: Context, urls: Array<String>, listener: Listener) {
            start(GoProber(context), urls, listener)
        }

        @JvmStatic
        internal fun start(prober: Prober, urls: Array<String>, listener: Listener) {
            val collector = Collector(urls.size, listener)
            urls.forEachIndexed { index, url -> prober.probe(url, Callback(index, collector)) }
        }
    }
}
