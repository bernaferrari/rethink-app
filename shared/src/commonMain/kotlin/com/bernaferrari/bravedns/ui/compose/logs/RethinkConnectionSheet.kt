/* Copyright 2026 RethinkDNS and its authors */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bernaferrari.bravedns.ui.compose.logs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bernaferrari.bravedns.ui.compose.firewall.RethinkRuleAction
import com.bernaferrari.bravedns.ui.compose.theme.RethinkChoiceOption
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConnectedChoiceGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard
import com.bernaferrari.bravedns.ui.compose.theme.RethinkKeyValueRow
import com.bernaferrari.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.bernaferrari.bravedns.ui.compose.theme.RethinkSheetHeader
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/** One button in the sheet. Hosts write the copy ("Allow Clock on Wi‑Fi") and own the effect. */
@Immutable
data class RethinkConnectionAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
)

/** What the sheet's rule group edits: the connection's domain when known, else its IP. */
enum class RethinkConnectionTargetKind { Domain, Ip }

/** Which rule the group shows and edits: this app's, or the one for every app. */
enum class RethinkConnectionRuleScope { App, AllApps }

/**
 * The rule group's state for [value] in [scope]. [action] is the *exact* rule for that target and
 * scope (None when there's none); a wildcard or range that also decides it is described in [note],
 * since editing the exact rule can't change it.
 */
@Immutable
data class RethinkConnectionRule(
    val kind: RethinkConnectionTargetKind,
    val value: String,
    val action: RethinkRuleAction,
    val scope: RethinkConnectionRuleScope,
    val note: String? = null,
    /** False while a write is in flight, so taps can't race it. */
    val enabled: Boolean = true,
)

/**
 * Everything the Connection sheet shows. Platform hosts build it from a log row (network or DNS)
 * plus a [BlockReason]-style mapping; the sheet itself holds no state beyond "details expanded".
 */
@Immutable
data class RethinkConnectionSheetModel(
    /** The domain, or the IP when no domain is known. */
    val title: String,
    /** "Clock · 09:41 · HTTPS". */
    val subtitle: String,
    val isBlocked: Boolean,
    /** One plain sentence: why it was blocked or allowed. */
    val verdict: String,
    /** A quieter line under [verdict], e.g. "Blocked by Hagezi Multi Pro, 1Hosts Pro". */
    val verdictCaption: String? = null,
    /** The fix derived from the rule (filled button). Null hides it, e.g. once applied. */
    val primaryAction: RethinkConnectionAction? = null,
    /** The tonal alternative under it ("Allow for 15 minutes"). */
    val secondaryAction: RethinkConnectionAction? = null,
    /** Block · Trust · No rule for the domain or IP. Null hides the group (no target known). */
    val rule: RethinkConnectionRule? = null,
    /** The key/value table the log row used to expand to; collapsed behind "Details". */
    val details: List<RethinkLogDetail> = emptyList(),
)

data class RethinkConnectionSheetStrings(
    /** Section title over the rule group: "This domain". */
    val thisDomain: String,
    /** "This IP". */
    val thisIp: String,
    val block: String,
    val trust: String,
    val noRule: String,
    /** The app scope, already naming the app: "Only Clock". */
    val onlyApp: String,
    val allApps: String,
    val details: String,
    val copyAddress: String,
    /** "Open Clock"; null hides the link (no app, or the host has nowhere to go). */
    val openApp: String? = null,
)

/**
 * The Connection sheet (journey b): what happened to one connection or lookup, why, and the fix
 * one tap away. Header (app + destination), a verdict card in the blocked/allowed container, the
 * primary and secondary fixes, a Block · Trust · No rule group with an Only app / All apps scope,
 * and footer links (open the app, copy the address, details).
 *
 * Stateless: every write goes through the callbacks, and hosts show the result with Undo in
 * [snackbarHostState] (drawn inside the sheet, above the scrim). See `ConnectionSheetHost` on
 * Android for the adapter that reads and writes the firewall managers.
 */
@Composable
fun RethinkConnectionSheet(
    model: RethinkConnectionSheetModel,
    strings: RethinkConnectionSheetStrings,
    onDismiss: () -> Unit,
    onRuleActionChange: (RethinkRuleAction) -> Unit,
    onRuleScopeChange: (RethinkConnectionRuleScope) -> Unit,
    modifier: Modifier = Modifier,
    appIcon: (@Composable () -> Unit)? = null,
    onCopyAddress: (() -> Unit)? = null,
    onOpenApp: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
) {
    RethinkModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier, includeBottomSpacer = false) { _ ->
        RethinkConnectionSheetContent(
            model = model,
            strings = strings,
            onRuleActionChange = onRuleActionChange,
            onRuleScopeChange = onRuleScopeChange,
            appIcon = appIcon,
            onCopyAddress = onCopyAddress,
            onOpenApp = onOpenApp,
        )
        snackbarHostState?.let { SnackbarHost(it, Modifier.fillMaxWidth()) }
        Spacer(Modifier.size(SharedDimensions.spacingLg))
    }
}

/**
 * The sheet's body without the modal chrome, for a list–detail pane (wide Logs) and for
 * screenshot tests. Lays out as a [Column] in the caller's scope.
 */
@Composable
fun ColumnScope.RethinkConnectionSheetContent(
    model: RethinkConnectionSheetModel,
    strings: RethinkConnectionSheetStrings,
    onRuleActionChange: (RethinkRuleAction) -> Unit,
    onRuleScopeChange: (RethinkConnectionRuleScope) -> Unit,
    appIcon: (@Composable () -> Unit)? = null,
    onCopyAddress: (() -> Unit)? = null,
    onOpenApp: (() -> Unit)? = null,
) {
    RethinkSheetHeader(title = model.title, subtitle = model.subtitle, leading = appIcon)
    VerdictCard(model)
    if (model.primaryAction != null || model.secondaryAction != null) {
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
            model.primaryAction?.let { action ->
                Button(
                    onClick = action.onClick,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = SharedDimensions.buttonHeight),
                ) { ActionContent(action) }
            }
            model.secondaryAction?.let { action ->
                FilledTonalButton(
                    onClick = action.onClick,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = SharedDimensions.buttonHeight),
                ) { ActionContent(action) }
            }
        }
    }
    model.rule?.let { rule -> RuleGroup(rule, strings, onRuleActionChange, onRuleScopeChange) }
    FooterLinks(model, strings, onCopyAddress, onOpenApp)
}

@Composable
private fun VerdictCard(model: RethinkConnectionSheetModel) {
    val container = if (model.isBlocked) RethinkStatusColors.blockedContainer else RethinkStatusColors.allowedContainer
    val content = if (model.isBlocked) RethinkStatusColors.onBlockedContainer else RethinkStatusColors.onAllowedContainer
    RethinkContainedCard(color = container, contentColor = content) {
        Row(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
            Icon(
                imageVector = if (model.isBlocked) MaterialSymbols.Filled.DoNotDisturbOn else MaterialSymbols.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(SharedDimensions.iconSizeMd),
            )
            Column(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                Text(model.verdict, style = MaterialTheme.typography.titleMedium)
                model.verdictCaption?.takeIf { it.isNotBlank() }?.let { caption ->
                    Text(caption, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ActionContent(action: RethinkConnectionAction) {
    action.icon?.let {
        Icon(it, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    }
    Text(action.label)
}

@Composable
private fun RuleGroup(
    rule: RethinkConnectionRule,
    strings: RethinkConnectionSheetStrings,
    onActionChange: (RethinkRuleAction) -> Unit,
    onScopeChange: (RethinkConnectionRuleScope) -> Unit,
) {
    Column {
        SectionHeader(if (rule.kind == RethinkConnectionTargetKind.Domain) strings.thisDomain else strings.thisIp)
        RethinkContainedCard(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
            SelectionContainer {
                Text(rule.value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            // "No rule" is an explicit option rather than deselecting, so the state always reads.
            RethinkConnectedChoiceGroup(
                options = listOf(
                    RethinkChoiceOption(RethinkRuleAction.Block, strings.block, MaterialSymbols.Filled.Block),
                    RethinkChoiceOption(RethinkRuleAction.Trust, strings.trust, MaterialSymbols.Filled.Verified),
                    RethinkChoiceOption(RethinkRuleAction.None, strings.noRule, MaterialSymbols.Filled.Remove),
                ),
                selected = rule.action.takeUnless { it == RethinkRuleAction.Bypass } ?: RethinkRuleAction.Trust,
                onSelect = { if (rule.enabled && it != rule.action) onActionChange(it) },
            )
            RethinkConnectedChoiceGroup(
                options = listOf(
                    RethinkChoiceOption(RethinkConnectionRuleScope.App, strings.onlyApp, MaterialSymbols.Filled.Android),
                    RethinkChoiceOption(RethinkConnectionRuleScope.AllApps, strings.allApps, MaterialSymbols.Filled.Apps),
                ),
                selected = rule.scope,
                onSelect = { if (rule.enabled) onScopeChange(it) },
            )
            rule.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FooterLinks(
    model: RethinkConnectionSheetModel,
    strings: RethinkConnectionSheetStrings,
    onCopyAddress: (() -> Unit)?,
    onOpenApp: (() -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs)) {
            if (onOpenApp != null && strings.openApp != null) {
                TextButton(onClick = onOpenApp) { Text(strings.openApp) }
            }
            if (onCopyAddress != null) {
                TextButton(onClick = onCopyAddress) {
                    Icon(MaterialSymbols.Filled.ContentCopy, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(strings.copyAddress)
                }
            }
            if (model.details.isNotEmpty()) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(strings.details)
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Icon(
                        MaterialSymbols.Filled.KeyboardArrowDown,
                        null,
                        Modifier.size(ButtonDefaults.IconSize).rotate(if (expanded) 180f else 0f),
                    )
                }
            }
        }
        AnimatedVisibility(expanded && model.details.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = SharedDimensions.spacingSm)) {
                model.details.forEach { detail ->
                    RethinkKeyValueRow(
                        label = detail.label,
                        value = detail.value,
                        modifier = Modifier.padding(vertical = SharedDimensions.spacingXs),
                        valueColor = if (detail.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        monospace = detail.monospace,
                        selectable = detail.monospace,
                    )
                }
            }
        }
    }
}
