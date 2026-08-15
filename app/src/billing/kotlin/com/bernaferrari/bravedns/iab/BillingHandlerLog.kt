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
package com.bernaferrari.bravedns.iab

import Logger
import Logger.LOG_IAB

/** Shared IAB log helpers so extracted collaborators keep the same `IABHandler` tag. */
internal object BillingHandlerLog {
    const val TAG = "IABHandler"

    fun d(methodName: String, msg: String) {
        Logger.d(LOG_IAB, "$TAG $methodName: $msg")
    }

    fun v(methodName: String, msg: String) {
        Logger.v(LOG_IAB, "$TAG $methodName: $msg")
    }

    fun e(methodName: String, msg: String, ex: Exception? = null) {
        Logger.e(LOG_IAB, "$TAG $methodName: $msg", ex)
    }

    fun i(methodName: String, msg: String) {
        Logger.i(LOG_IAB, "$TAG $methodName: $msg")
    }
}
