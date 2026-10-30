package com.bernaferrari.bravedns.util

import Logger
import android.os.Build
import android.view.Window

/** Applies to both the main activity and independently hosted firewall controls. */
object SensitiveWindowProtection {
    fun apply(window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            window.setHideOverlayWindows(true)
        } catch (error: SecurityException) {
            Logger.w(Logger.LOG_TAG_UI, "Cannot hide overlay windows", error)
        }
    }
}
