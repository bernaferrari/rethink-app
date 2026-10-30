package com.bernaferrari.bravedns.database

import android.content.pm.ApplicationInfo
import com.bernaferrari.bravedns.util.AndroidUidConfig

internal object NewAppNotificationPolicy {
    fun eligible(packageName: String, ownPackage: String, info: ApplicationInfo?): Boolean {
        if (packageName == ownPackage || info == null) return false
        val systemComponent = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 && !AndroidUidConfig.isUidAppRange(info.uid % 100_000)
        return !systemComponent
    }
}
