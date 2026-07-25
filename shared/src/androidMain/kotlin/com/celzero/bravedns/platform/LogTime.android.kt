package com.celzero.bravedns.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatLogTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(epochMillis))
