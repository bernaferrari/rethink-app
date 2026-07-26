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
package com.bernaferrari.bravedns.image

import Logger
import Logger.LOG_TAG_DNS
import android.content.Context
import android.os.Process
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.bernaferrari.bravedns.util.Utilities
import com.bernaferrari.bravedns.util.ExpiringCache
import kotlinx.coroutines.runBlocking

/**
 * FavIconDownloader - Downloads the favicon of the DNS requests and stores it in the Coil cache.
 * The fav icon will later be retrieved by the DNS query list / by the bottom sheet in the DNS log
 * screen.
 *
 * The runnable will be executed only if the show fav icon setting is turned on. In Settings -> DNS
 * -> Show fav icon (_TRUE_).
 */
class FavIconDownloader(val context: Context, private val url: String) : Runnable {

    companion object {
        private const val FAV_ICON_DUCK_URL = "https://icons.duckduckgo.com/ip2/"
        private const val FAV_ICON_NEXTDNS_BASE_URL = "https://favicons.nextdns.io/"
        private const val FAV_ICON_SIZE = "@2x.png"
        private const val CACHE_BUILDER_MAX_SIZE = 10000L

        private val failedFavIconUrls: ExpiringCache<String, Boolean> =
            ExpiringCache(maxSize = CACHE_BUILDER_MAX_SIZE, expireAfterWriteMs = Long.MAX_VALUE / 2)

        fun getDomainUrlFromFdqnDuckduckgo(url: String): String {
            val domainUrl = Utilities.getETldPlus1(url).toString()
            return constructFavUrlDuckDuckGo(domainUrl)
        }

        fun constructFavUrlDuckDuckGo(url: String): String = "${FAV_ICON_DUCK_URL}${url}.ico"

        fun constructFavIcoUrlNextDns(url: String): String = "$FAV_ICON_NEXTDNS_BASE_URL$url$FAV_ICON_SIZE"

        fun isUrlAvailableInFailedCache(url: String): Boolean? = failedFavIconUrls.getIfPresent(url)
    }

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)

        val fdqnUrl = url.dropLastWhile { it == '.' }

        if (failedFavIconUrls.getIfPresent(fdqnUrl) == null) {
            fetchFromNextDns(fdqnUrl)
        }
    }

    private fun fetchFromNextDns(url: String) {
        val subUrl = constructFavIcoUrlNextDns(url)
        val success = downloadToCache(subUrl)
        if (!success) {
            Logger.i(LOG_TAG_DNS, "Coil, load failure from nextdns $subUrl")
            updateImage(
                url,
                constructFavUrlDuckDuckGo(url),
                getDomainUrlFromFdqnDuckduckgo(url),
                true,
            )
        } else {
            Logger.d(LOG_TAG_DNS, "Coil, load success from nextdns for url: $url")
        }
    }

    private fun updateImage(fdqnUrl: String, subUrl: String, url: String, retry: Boolean) {
        val success = downloadToCache(subUrl)
        if (success) {
            Logger.d(LOG_TAG_DNS, "Coil, downloaded from duckduckgo $subUrl, $url")
            return
        }

        if (retry) {
            Logger.d(LOG_TAG_DNS, "Coil, download failed from duckduckgo $subUrl")
            updateImage(fdqnUrl, url, "", false)
        } else {
            failedFavIconUrls.put(fdqnUrl, true)
            Logger.i(LOG_TAG_DNS, "Coil, no fav icon available for the url: $subUrl")
        }
    }

    private fun downloadToCache(imageUrl: String): Boolean =
        runBlocking {
            val loader = FavIconImageLoader.get(context)
            val result =
                loader.execute(
                    ImageRequest.Builder(context.applicationContext)
                        .data(imageUrl)
                        .build(),
                )
            result is SuccessResult
        }
}