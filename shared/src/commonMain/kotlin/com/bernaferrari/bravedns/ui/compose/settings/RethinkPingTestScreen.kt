@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.PrimaryButton
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkStatusColors
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    androidx.compose.material3.Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
                subtitle = strings.subtitle,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(
                start = SharedDimensions.screenPaddingHorizontal,
                end = SharedDimensions.screenPaddingHorizontal,
                top = SharedDimensions.spacingMd,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            item { PingCheckSection(strings.ipSection, ipChecks, strings, onValueChange) }
            item { PingCheckSection(strings.hostSection, hostChecks, strings, onValueChange) }
            item {
                PrimaryButton(text = strings.test, onClick = onTest, modifier = Modifier.fillMaxWidth())
            }
            strength?.let { value ->
                item {
                    SectionHeader(strings.strength)
                    PingCheckCard {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = SharedDimensions.cardPadding,
                                vertical = SharedDimensions.spacingLg,
                            ),
                            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingMd),
                        ) {
                            Text(
                                strings.strengthValue(value, maxStrength),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { value.toFloat() / maxStrength.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
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

@Composable
private fun PingCheckCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SharedDimensions.groupCornerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                Icon(
                    if (status.success) MaterialSymbols.Filled.CheckCircle else MaterialSymbols.Filled.Cancel,
                    contentDescription = if (showLabel) null else label,
                    modifier = Modifier.size(SharedDimensions.iconSizeSm),
                    tint = color,
                )
                if (showLabel) Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}
