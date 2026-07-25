package com.celzero.bravedns.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

actual fun formatLogTime(epochMillis: Long): String =
    NSDateFormatter().apply {
        dateFormat = "HH:mm:ss"
        locale = NSLocale("en_US_POSIX")
    }.stringFromDate(NSDate(timeIntervalSince1970 = epochMillis / 1_000.0))
