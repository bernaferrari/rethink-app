/*
 * Copyright 2025 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.util

import Logger
import Logger.LOG_TAG_UI
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bernaferrari.bravedns.R

/**
 * Utility class to handle SSID-related permissions and operations.
 * This class manages the complex permission requirements for accessing WiFi SSID information
 * which requires both WiFi state and location permissions on different Android versions.
 */
object SsidPermissionManager {

    // Request code for SSID permissions
    const val SSID_PERMISSION_REQUEST_CODE = 1001

    // Required permissions for SSID access
    private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /**
     * Check if all required permissions for SSID access are granted
     * @param context The context to check permissions
     * @return true if all permissions are granted, false otherwise
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBackgroundAccess(context: Context): Boolean =
        hasRequiredPermissions(context) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED)

    /** Foreground location must be granted before requesting background access. */
    fun requestBackgroundAccess(activity: Activity) {
        if (!isLocationEnabled(activity)) {
            requestLocationEnable(activity)
        } else if (!hasRequiredPermissions(activity)) {
            requestSsidPermissions(activity)
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(activity,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), SSID_PERMISSION_REQUEST_CODE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            openAppSettings(activity)
        }
    }

    /**
     * Request the required permissions for SSID access
     * @param activity The activity to request permissions from
     */
    fun requestSsidPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_PERMISSIONS,
            SSID_PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Open the app's settings page for manual permission management
     * @param context The context to start the settings intent
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Logger.w(LOG_TAG_UI, "openAppSettings primary intent failed: ${e.message}", e)
            try {
                val fallbackIntent = Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)
                context.startActivity(fallbackIntent)
            } catch (fallback: android.content.ActivityNotFoundException) {
                Logger.w(LOG_TAG_UI, "openAppSettings fallback intent failed: ${fallback.message}", fallback)
                // If all else fails, show a toast message
                if (context is Activity) {
                    Utilities.showToastUiCentered(
                        context,
                        context.getString(R.string.intent_launch_error, "App Settings"),
                        android.widget.Toast.LENGTH_LONG
                    )
                }
            }
        }
    }

    /**
     * Check if location services are enabled on the device
     * @param context The context to access system services
     * @return true if location services are enabled, false otherwise
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Show a user-friendly explanation for why permissions are needed
     * @param context The context to get string resources
     * @return Explanation message for permission request
     */
    fun getPermissionExplanation(context: Context): String {
        return context.getString(R.string.ssid_permission_explanation, context.getString(R.string.lbl_ssids))
    }

    fun getLocationEnableExplanation(context: Context): String {
        return context.getString(R.string.location_enable_explanation, context.getString(R.string.lbl_ssids))
    }

    /**
     * Validate if the device supports WiFi and location services
     * @param context The context to check capabilities
     * @return true if device supports required features, false otherwise
     */
    fun isDeviceSupported(context: Context): Boolean {
        val packageManager = context.packageManager
        val hasWifi = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
        val hasLocation = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)
        return hasWifi && hasLocation
    }

    /**
     * Request user to enable location services by opening location settings
     * @param context The context to start the location settings intent
     */
    fun requestLocationEnable(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Logger.w(LOG_TAG_UI, "requestLocationEnable primary intent failed: ${e.message}", e)
            try {
                val fallbackIntent = Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
                context.startActivity(fallbackIntent)
            } catch (fallback: android.content.ActivityNotFoundException) {
                Logger.w(LOG_TAG_UI, "requestLocationEnable fallback intent failed: ${fallback.message}", fallback)
                // If all else fails, show a toast message
                if (context is Activity) {
                    android.widget.Toast.makeText(
                        context,
                        getPermissionExplanation(context),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
