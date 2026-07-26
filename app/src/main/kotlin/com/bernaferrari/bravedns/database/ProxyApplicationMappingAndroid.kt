package com.bernaferrari.bravedns.database
import android.Manifest
import android.content.pm.PackageManager
import com.bernaferrari.bravedns.database.AppInfoRepository.Companion.NO_PACKAGE_PREFIX
fun ProxyApplicationMapping.hasInternetPermission(packageManager: PackageManager): Boolean {
    if (packageName.startsWith(NO_PACKAGE_PREFIX)) return true
    return packageManager.checkPermission(Manifest.permission.INTERNET, packageName) == PackageManager.PERMISSION_GRANTED
}
