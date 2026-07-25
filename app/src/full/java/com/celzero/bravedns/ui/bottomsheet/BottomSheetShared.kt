/* Copyright 2026 RethinkDNS and its authors */
package com.celzero.bravedns.ui.bottomsheet

import Logger
import Logger.LOG_TAG_UI
import android.content.Context
import android.graphics.drawable.Drawable
import com.celzero.bravedns.R
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.Utilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android app-identity and mutation adapters for the shared rule-sheet renderers. */
suspend fun fetchRuleSheetAppIdentity(context: Context, uid: Int): Pair<List<String>, Drawable?> {
    val appNames = FirewallManager.getAppNamesByUid(uid)
    val packageName = appNames.firstOrNull()?.let { FirewallManager.getPackageNameByAppName(it) }
    val icon = if (packageName.isNullOrEmpty()) null else Utilities.getIcon(context, packageName)
    return appNames to icon
}

fun formatRuleSheetAppName(context: Context, appNames: List<String>): String? =
    when {
        appNames.isEmpty() -> null
        appNames.size >= 2 -> context.getString(R.string.ctbs_app_other_apps, appNames[0], appNames.size.minus(1).toString())
        else -> appNames[0]
    }

fun formatCustomRuleSheetAppName(context: Context, uid: Int, appNames: List<String>): String =
    when {
        uid == UID_EVERYBODY -> context.getString(R.string.firewall_act_universal_tab)
        appNames.isEmpty() -> context.getString(R.string.network_log_app_name_unknown) + " ($uid)"
        appNames.size >= 2 -> context.getString(R.string.ctbs_app_other_apps, appNames[0], appNames.size.minus(1).toString())
        else -> appNames[0]
    }

fun logFirewallRuleChange(
    eventLogger: EventLogger,
    title: String,
    details: String,
    tag: String? = null,
) {
    eventLogger.log(EventType.FW_RULE_MODIFIED, Severity.LOW, title, EventSource.UI, false, details)
    tag?.let { Logger.v(LOG_TAG_UI, "$it $details") }
}

fun <T> launchRuleMutation(
    scope: CoroutineScope,
    mutation: suspend () -> T,
    onUpdated: (T) -> Unit,
) {
    scope.launch(Dispatchers.IO) {
        val result = mutation()
        withContext(Dispatchers.Main) { onUpdated(result) }
    }
}
