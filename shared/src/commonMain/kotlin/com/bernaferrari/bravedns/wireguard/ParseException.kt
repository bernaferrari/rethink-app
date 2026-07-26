/*
 * Copyright 2023 RethinkDNS and its authors
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.bernaferrari.bravedns.wireguard

/** Multiplatform parse error; [parsingClassName] replaces java.lang.Class. */
class ParseException(
    val parsingClassName: String,
    val text: CharSequence,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    constructor(parsingClassName: String, text: CharSequence, cause: Throwable?) :
        this(parsingClassName, text, null, cause)
}
