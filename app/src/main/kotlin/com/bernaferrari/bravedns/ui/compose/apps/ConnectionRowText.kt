/*
 * Copyright 2021 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.ui.compose.apps

import kotlin.math.log2

/** A stored comma list ("a,,b,") as readable text ("a, b"). */
internal fun commaList(value: String?): String =
    value.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")

/**
 * How full a connection row's activity bar is: grows with the log of the count, so one hit shows a
 * sliver and 32 or more fill it, and a busier row never draws shorter than a quieter one.
 */
internal fun connectionActivity(count: Int): Float =
    if (count <= 1) 0.1f else (log2(count.toDouble()) / 5).toFloat().coerceIn(0.1f, 1f)
