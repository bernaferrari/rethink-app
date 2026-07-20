package com.celzero.bravedns.platform

import kotlin.js.Date

actual fun currentTimeMillis(): Long = Date().getTime().toLong()
