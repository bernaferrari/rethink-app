/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.firewall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormActionRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormSection
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

val RethinkRuleSheetBottomPaddingWithActions: Dp = SharedDimensions.spacing3xl + SharedDimensions.spacingMd
val RethinkRuleSheetBottomPaddingCompact: Dp = SharedDimensions.spacing2xl + SharedDimensions.spacingSm

/** Target-neutral base sheet for firewall-rule editing flows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkRuleSheetModal(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    RethinkModalBottomSheet(
        onDismissRequest = onDismissRequest,
        contentPadding = PaddingValues(SharedDimensions.spacingNone),
        verticalSpacing = SharedDimensions.spacingNone,
        includeBottomSpacer = false,
        // Rule editors and the Android LAN/reachability wrappers contain save actions. Opening
        // them fully avoids a partially-expanded sheet hiding those actions on short viewports.
        expandOnShow = true,
        content = { _ -> content() },
    )
}

@Composable
fun RethinkRuleSheetLayout(
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = SharedDimensions.spacingMd,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

/**
 * Who a rule added from [RethinkAddRuleSheet] applies to. Hosts map [AllApps] to their universal
 * uid. For an IP, Trust for all apps means the universal "bypass" status (an all-apps Trust IP
 * rule has no effect in the firewall), so hosts must write that instead.
 */
@Immutable
sealed interface RethinkAddRuleScope {
    data object AllApps : RethinkAddRuleScope

    /** One app (or every package sharing its uid); [label] is shown on the scope option. */
    data class App(val uid: Int, val label: String) : RethinkAddRuleScope
}

data class RethinkAddRuleStrings(
    /** "Add rule". */
    val title: String,
    val domainLabel: String,
    /** "e.g. ads.example.com or *.example.com". */
    val domainHint: String,
    /** "IP or range". */
    val ipLabel: String,
    /** "e.g. 1.2.3.4 or 1.2.3.0/24, optional :port". */
    val ipHint: String,
    val action: String,
    val block: String,
    val trust: String,
    /** "Applies to". */
    val appliesTo: String,
    val allApps: String,
    /** "Choose app…": the app option's label until an app is chosen. */
    val chooseApp: String,
    /** The confirm label follows target and action: "Block domain", "Trust IP"… */
    val confirm: (RethinkRulesTab, RethinkRuleAction) -> String,
    val cancel: String,
)

/**
 * Add a domain or IP rule as one sentence: a target ([target] picks the field), an **Action**
 * (Block · Trust, Block preselected so typing and confirming still blocks, as Add always did), and
 * **Applies to** (All apps · one app). Used by the Rules screen and the Connection sheet.
 *
 * [scope] is hoisted because choosing an app is the host's picker: [onChooseApp] opens it (tapping
 * the app option again re-opens it), and the host feeds the result back through [scope]. With a
 * null [onChooseApp] the scope can't change to another app (the Rules screen scoped to one app).
 *
 * [onAdd] validates and persists; an accepted value closes the sheet (animated, then [onDismiss]),
 * a rejected one keeps it open with the result's error.
 */
@Composable
fun RethinkAddRuleSheet(
    target: RethinkRulesTab,
    scope: RethinkAddRuleScope,
    strings: RethinkAddRuleStrings,
    onScopeChange: (RethinkAddRuleScope) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (value: String, action: RethinkRuleAction, scope: RethinkAddRuleScope) -> RethinkRulesAddResult,
    modifier: Modifier = Modifier,
    initialAction: RethinkRuleAction = RethinkRuleAction.Block,
    initialValue: String = "",
    onChooseApp: (() -> Unit)? = null,
) {
    RethinkModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier, expandOnShow = true) { dismiss ->
        RethinkAddRuleSheetContent(
            target = target,
            scope = scope,
            strings = strings,
            onScopeChange = onScopeChange,
            onDismiss = dismiss,
            onAdd = { value, action, ruleScope -> onAdd(value, action, ruleScope).also { if (it.accepted) dismiss() } },
            initialAction = initialAction,
            initialValue = initialValue,
            onChooseApp = onChooseApp,
        )
    }
}

/** [RethinkAddRuleSheet]'s body without the modal chrome (screenshot tests, inline editors). */
@Composable
fun ColumnScope.RethinkAddRuleSheetContent(
    target: RethinkRulesTab,
    scope: RethinkAddRuleScope,
    strings: RethinkAddRuleStrings,
    onScopeChange: (RethinkAddRuleScope) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (value: String, action: RethinkRuleAction, scope: RethinkAddRuleScope) -> RethinkRulesAddResult,
    initialAction: RethinkRuleAction = RethinkRuleAction.Block,
    initialValue: String = "",
    onChooseApp: (() -> Unit)? = null,
) {
    var value by rememberSaveable(target) { mutableStateOf(initialValue) }
    var action by rememberSaveable(target) { mutableStateOf(initialAction.takeIf { it == RethinkRuleAction.Trust } ?: RethinkRuleAction.Block) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    val isIp = target == RethinkRulesTab.IP
    val submit = { error = onAdd(value.trim(), action, scope).error }

    RethinkSheetHeader(strings.title)
    RethinkFormTextField(
        value = value,
        onValueChange = { value = it; error = null },
        label = if (isIp) strings.ipLabel else strings.domainLabel,
        placeholder = if (isIp) strings.ipHint else strings.domainHint,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        // Uri: no autocorrect or capitals, and both "." and "/" (ranges, wildcards) on the keyboard.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
    )
    RethinkFormSection(title = strings.action) {
        RethinkConnectedChoiceGroup(
            options = listOf(
                RethinkChoiceOption(RethinkRuleAction.Block, strings.block, MaterialSymbols.Filled.Block),
                RethinkChoiceOption(RethinkRuleAction.Trust, strings.trust, MaterialSymbols.Filled.Verified),
            ),
            selected = action,
            onSelect = { action = it },
        )
    }
    RethinkFormSection(title = strings.appliesTo) {
        val app = scope as? RethinkAddRuleScope.App
        RethinkConnectedChoiceGroup(
            options = listOfNotNull(
                RethinkChoiceOption<RethinkAddRuleScope?>(RethinkAddRuleScope.AllApps, strings.allApps, MaterialSymbols.Filled.Apps),
                // The app option carries the chosen app; without a picker only a preset app shows.
                if (app != null || onChooseApp != null) {
                    RethinkChoiceOption<RethinkAddRuleScope?>(app, app?.label ?: strings.chooseApp, MaterialSymbols.Filled.Android)
                } else {
                    null
                },
            ),
            selected = scope,
            onSelect = { picked ->
                when {
                    picked == RethinkAddRuleScope.AllApps -> onScopeChange(RethinkAddRuleScope.AllApps)
                    picked is RethinkAddRuleScope.App -> onScopeChange(picked)
                    else -> onChooseApp?.invoke()
                }
            },
            onDeselect = { if (app != null) onChooseApp?.invoke() },
        )
    }
    RethinkFormActionRow(
        confirmLabel = strings.confirm(target, action),
        onConfirm = submit,
        dismissLabel = strings.cancel,
        onDismiss = onDismiss,
        confirmEnabled = value.isNotBlank(),
    )
}
