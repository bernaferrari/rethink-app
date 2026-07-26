/*
 * Copyright 2026 RethinkDNS and its authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package com.bernaferrari.bravedns.ui.dialog

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.SsidItem
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardSsidEditor
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardSsidEditorStrings
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardSsidRule
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardSsidType
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardDialog
import com.bernaferrari.bravedns.ui.compose.wireguard.RethinkWireguardDialogColumn
import com.bernaferrari.bravedns.util.Utilities

/** Android storage and toast adapter for the shared Wi-Fi-rule editor. */
@Composable
fun WgSsidDialog(
    currentSsids: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    RethinkWireguardDialog(onDismissRequest = onDismiss) {
        RethinkWireguardDialogColumn(scrollable = true) {
            RethinkWireguardSsidEditor(
                initialRules = SsidItem.parseStorageList(currentSsids).map { it.toRethinkRule() },
                strings = RethinkWireguardSsidEditorStrings(
                    title = stringResource(R.string.wg_setting_ssid_title),
                    action = stringResource(R.string.lbl_action),
                    criteria = stringResource(R.string.lbl_criteria),
                    ssid = stringResource(R.string.lbl_ssid),
                    connect = stringResource(R.string.lbl_connect),
                    pause = stringResource(R.string.notification_action_pause_vpn),
                    exact = stringResource(R.string.wg_ssid_type_exact),
                    wildcard = stringResource(R.string.wg_ssid_type_wildcard),
                    add = stringResource(R.string.lbl_add),
                    save = stringResource(R.string.fapps_info_dialog_positive_btn),
                    cancel = stringResource(R.string.lbl_cancel),
                    delete = stringResource(R.string.lbl_delete),
                    invalidName = stringResource(R.string.wg_ssid_invalid_error, stringResource(R.string.lbl_ssids)),
                    description = { action, criteria ->
                        context.getString(
                            R.string.wg_ssid_dialog_description,
                            action,
                            context.getString(R.string.lbl_ssid),
                            criteria.lowercase(),
                        )
                    },
                ),
                onSave = { rules ->
                    onSave(SsidItem.toStorageList(rules.map { it.toStorageRule() }))
                    onDismiss()
                },
                onDismiss = onDismiss,
                onValidationError = { message ->
                    Utilities.showToastUiCentered(context, message, Toast.LENGTH_SHORT)
                },
            )
        }
    }
}

private fun SsidItem.toRethinkRule() = RethinkWireguardSsidRule(
    name = name,
    type = when (type) {
        SsidItem.SsidType.EQUAL_EXACT -> RethinkWireguardSsidType.EqualExact
        SsidItem.SsidType.EQUAL_WILDCARD -> RethinkWireguardSsidType.EqualWildcard
        SsidItem.SsidType.NOTEQUAL_EXACT -> RethinkWireguardSsidType.NotEqualExact
        SsidItem.SsidType.NOTEQUAL_WILDCARD -> RethinkWireguardSsidType.NotEqualWildcard
    },
)

private fun RethinkWireguardSsidRule.toStorageRule() = SsidItem(
    name = name,
    type = when (type) {
        RethinkWireguardSsidType.EqualExact -> SsidItem.SsidType.EQUAL_EXACT
        RethinkWireguardSsidType.EqualWildcard -> SsidItem.SsidType.EQUAL_WILDCARD
        RethinkWireguardSsidType.NotEqualExact -> SsidItem.SsidType.NOTEQUAL_EXACT
        RethinkWireguardSsidType.NotEqualWildcard -> SsidItem.SsidType.NOTEQUAL_WILDCARD
    },
)
