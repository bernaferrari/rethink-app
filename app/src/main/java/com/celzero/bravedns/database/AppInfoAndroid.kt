package com.celzero.bravedns.database

import android.Manifest
import android.content.pm.PackageManager
import com.celzero.bravedns.database.AppInfoRepository.Companion.NO_PACKAGE_PREFIX

fun AppInfo.hasInternetPermission(packageManager: PackageManager): Boolean {
    if (packageName.startsWith(NO_PACKAGE_PREFIX)) return true
    return try {
        packageManager.checkPermission(Manifest.permission.INTERNET, packageName) ==
            PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        true
    }
}
