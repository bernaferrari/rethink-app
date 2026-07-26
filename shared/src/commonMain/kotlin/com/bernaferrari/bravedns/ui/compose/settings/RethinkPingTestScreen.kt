/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.bernaferrari.bravedns.ui.compose.theme.PrimaryButton
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
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
        topBar = {
            RethinkLargeTopBar(
                title = strings.title,
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
                top = SharedDimensions.spacingSm,
                bottom = SharedDimensions.spacing3xl,
            ),
            verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingLg),
        ) {
            item {
                Text(
                    text = strings.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SectionHeader(strings.ipSection)
                Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    ipChecks.forEach { check ->
                        PingCheckField(check, onValueChange)
                    }
                }
            }
            item {
                SectionHeader(strings.hostSection)
                Column(verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
                    hostChecks.forEach { check ->
                        PingCheckField(check, onValueChange)
                    }
                }
            }
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
        shape = RoundedCornerShape(SharedDimensions.cornerRadius4xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content,
    )
}

@Composable
private fun PingCheckField(
    check: RethinkPingCheck,
    onValueChange: (String, String) -> Unit,
) {
    RethinkFormTextField(
        value = check.value,
        onValueChange = { onValueChange(check.id, it) },
        label = null,
        readOnly = !check.editable,
        singleLine = true,
        trailingIcon = {
            when (val status = check.status) {
                RethinkPingStatus.Idle -> Unit
                RethinkPingStatus.Loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                is RethinkPingStatus.Result -> Icon(
                    if (status.success) MaterialSymbols.Filled.CheckCircle else MaterialSymbols.Filled.Cancel,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (status.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}
