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
package com.bernaferrari.bravedns.image

import android.content.Context
import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.bernaferrari.bravedns.network.HttpClientManager

object FavIconImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance
                ?: ImageLoader.Builder(context.applicationContext)
                    .components {
                        add(KtorNetworkFetcherFactory(HttpClientManager.imageClient()))
                    }
                    .build()
                    .also { instance = it }
        }
}