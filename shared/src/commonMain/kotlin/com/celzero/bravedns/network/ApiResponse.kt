/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.network

import io.ktor.utils.io.ByteReadChannel

data class ApiResponse<T>(
    val code: Int,
    val body: T?,
    val message: String,
    val requestUrl: String = "",
) {
    val isSuccessful: Boolean
        get() = code in 200..299
}

/** Streaming download body kept as a Ktor [ByteReadChannel] for multiplatform use. */
data class StreamingBody(
    val channel: ByteReadChannel,
    val contentLength: Long,
)
