/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.AppInfo
import com.bernaferrari.bravedns.service.FirewallManager
import com.bernaferrari.bravedns.ui.compose.common.AppIcon

/**
 * One "choose an app" flow for rule scopes (the Rules scope chip and the Add rule sheet's
 * "Choose app…"). [open] shows [RethinkAppPickerSheet] and hands the pick to its callback.
 */
class RuleAppPicker internal constructor() {
    internal var onPicked by mutableStateOf<((RethinkAddRuleScope.App) -> Unit)?>(null)

    fun open(onPicked: (RethinkAddRuleScope.App) -> Unit) {
        this.onPicked = onPicked
    }
}

@Composable
fun rememberRuleAppPicker(): RuleAppPicker = remember { RuleAppPicker() }

/** Renders the picker while one is open. Apps come from [FirewallManager]'s live list, one row per uid. */
@Composable
fun RuleAppPickerHost(picker: RuleAppPicker, selectedUid: Int? = null) {
    val onPicked = picker.onPicked ?: return
    val apps by remember { FirewallManager.appListFlow() }.collectAsState()
    val choices = remember(apps) { apps.toPickableApps() }
    RethinkAppPickerSheet(
        // The list is empty only until FirewallManager has loaded the installed apps.
        apps = choices.takeIf { it.isNotEmpty() },
        strings = RethinkAppPickerStrings(
            title = stringResource(R.string.rules_choose_app_title),
            searchPlaceholder = stringResource(R.string.rules_choose_app_search),
            clearSearch = stringResource(R.string.cd_clear_search),
            loading = stringResource(R.string.lbl_loading),
            noResults = stringResource(R.string.rules_choose_app_empty),
        ),
        selectedUid = selectedUid,
        onPick = { app ->
            picker.onPicked = null
            onPicked(RethinkAddRuleScope.App(app.uid, app.label))
        },
        onDismiss = { picker.onPicked = null },
        appIcon = { app -> AppIcon(packageName = app.iconKey, appName = app.label) },
    )
}

/** One choice per uid (packages sharing a uid share its rules); uninstalled apps are left out. */
private fun Collection<AppInfo>.toPickableApps(): List<RethinkPickableApp> =
    filter { it.tombstoneTs == 0L }
        .groupBy { it.uid }
        .map { (uid, infos) ->
            val sorted = infos.sortedBy { it.appName.lowercase() }
            val first = sorted.first()
            val others = sorted.drop(1).map { it.appName }.filter { it.isNotBlank() }
            RethinkPickableApp(
                uid = uid,
                label = first.appName.ifBlank { first.packageName },
                detail = if (others.isEmpty()) first.packageName else others.joinToString(", "),
                iconKey = first.packageName,
            )
        }
        .sortedBy { it.label.lowercase() }
