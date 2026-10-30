@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.PrimaryButton
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusIndicator
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusLabel
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBarLazyColumnScreen
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.RethinkContainedCard

sealed interface RethinkPingStatus {
    data object Idle : RethinkPingStatus
    data object Loading : RethinkPingStatus
    data class Result(val success: Boolean) : RethinkPingStatus
}

data class RethinkPingCheck(
    val id: String,
    val value: String,
    val editable: Boolean,
    val status: RethinkPingStatus,
)

data class RethinkPingTestStrings(
    val title: String,
    val subtitle: String,
    val noVpnTitle: String,
    val noVpnDescription: String,
    val dismiss: String,
    val ipSection: String,
    val hostSection: String,
    val test: String,
    val strength: String,
    val strengthValue: @Composable (Int, Int) -> String,
    val reachable: String,
    val unreachable: String,
    val testing: String,
    val customTarget: String,
)

/** Portable connectivity-check form; hosts perform the actual reachability work. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkPingTestScreen(
    ipChecks: List<RethinkPingCheck>,
    hostChecks: List<RethinkPingCheck>,
    strength: Int?,
    maxStrength: Int,
    vpnActive: Boolean,
    strings: RethinkPingTestStrings,
    onValueChange: (id: String, value: String) -> Unit,
    onTest: () -> Unit,
    onBackClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var showVpnRequiredDialog by remember(vpnActive) { mutableStateOf(!vpnActive) }
    RethinkTopBarLazyColumnScreen(
        title = strings.title,
        subtitle = strings.subtitle,
        onBackClick = onBackClick,
        modifier = modifier,
    ) {
        item { PingCheckSection(strings.ipSection, ipChecks, strings, onValueChange) }
        item { PingCheckSection(strings.hostSection, hostChecks, strings, onValueChange) }
        item {
            PrimaryButton(text = strings.test, onClick = onTest, modifier = Modifier.fillMaxWidth())
        }
        strength?.let { value ->
            item {
                SectionHeader(strings.strength)
                RethinkContainedCard(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd)) {
                    Text(
                        strings.strengthValue(value, maxStrength),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearWavyProgressIndicator(
                        progress = { value.toFloat() / maxStrength.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (showVpnRequiredDialog) {
        RethinkConfirmDialog(
            onDismissRequest = { showVpnRequiredDialog = false },
            title = strings.noVpnTitle,
            message = strings.noVpnDescription,
            confirmText = strings.dismiss,
            onConfirm = { onBackClick?.invoke() },
        )
    }
}

/**
 * Fixed targets are read-only, so they render as a grouped list with monospace values rather than
 * as disabled-looking inputs; only the custom target is an editable field. Each row reports its
 * own result inline.
 */
@Composable
private fun PingCheckSection(
    title: String,
    checks: List<RethinkPingCheck>,
    strings: RethinkPingTestStrings,
    onValueChange: (String, String) -> Unit,
) {
    val fixed = checks.filterNot { it.editable }
    val editable = checks.filter { it.editable }
    // The header hugs its group like every other section; spacing only separates group and field.
    Column {
        SectionHeader(title)
        Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
        if (fixed.isNotEmpty()) {
            RethinkListGroup {
                fixed.forEachIndexed { index, check ->
                    RethinkListItem(
                        headline = check.value,
                        headlineAnnotated = AnnotatedString(check.value, SpanStyle(fontFamily = FontFamily.Monospace)),
                        position = cardPositionFor(index, fixed.lastIndex),
                        trailing = { PingStatusIndicator(check.status, strings) },
                    )
                }
            }
        }
        editable.forEach { check ->
            RethinkFormTextField(
                value = check.value,
                onValueChange = { onValueChange(check.id, it) },
                label = strings.customTarget,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                trailingIcon = { PingStatusIndicator(check.status, strings, showLabel = false) },
            )
        }
        }
    }
}

@Composable
private fun PingStatusIndicator(status: RethinkPingStatus, strings: RethinkPingTestStrings, showLabel: Boolean = true) {
    when (status) {
        RethinkPingStatus.Idle -> Unit
        RethinkPingStatus.Loading -> LoadingIndicator(
            Modifier.size(SharedDimensions.iconSizeMd).semantics { contentDescription = strings.testing },
        )
        is RethinkPingStatus.Result -> {
            val color = if (status.success) RethinkStatusColors.allowed else RethinkStatusColors.blocked
            val label = if (status.success) strings.reachable else strings.unreachable
            val icon = if (status.success) MaterialSymbols.Filled.CheckCircle else MaterialSymbols.Filled.Cancel
            if (showLabel) {
                RethinkStatusLabel(
                    label = label,
                    color = color,
                    indicator = RethinkStatusIndicator.Icon(icon),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                Icon(icon, contentDescription = label, modifier = Modifier.size(SharedDimensions.iconSizeSm), tint = color)
            }
        }
    }
}
