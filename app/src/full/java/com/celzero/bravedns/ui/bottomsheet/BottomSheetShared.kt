/*
 * Copyright 2026 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.bottomsheet

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.celzero.bravedns.R
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.EventType
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.service.EventLogger
import com.celzero.bravedns.service.FirewallManager
import com.celzero.bravedns.ui.compose.rememberDrawablePainter
import com.celzero.bravedns.ui.compose.theme.Dimensions
import com.celzero.bravedns.ui.compose.theme.RethinkFilterChip
import com.celzero.bravedns.ui.compose.theme.RethinkConfirmDialog
import com.celzero.bravedns.ui.compose.theme.RethinkModalBottomSheet
import com.celzero.bravedns.ui.compose.theme.RethinkTwoOptionSegmentedRow
import com.celzero.bravedns.util.Constants.Companion.UID_EVERYBODY
import com.celzero.bravedns.util.Utilities
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RuleSheetChipColors(
    val neutralText: Color,
    val neutralBg: Color,
    val negativeText: Color,
    val negativeBg: Color,
    val positiveText: Color,
    val positiveBg: Color
)

data class RuleSheetChipOption(
    val label: String,
    val selected: Boolean,
    val selectedText: Color,
    val selectedContainer: Color,
    val onClick: () -> Unit
)

val RuleSheetBottomPaddingWithActions: Dp = Dimensions.spacing3xl + Dimensions.spacingMd
val RuleSheetBottomPaddingCompact: Dp = Dimensions.spacing2xl + Dimensions.spacingSm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleSheetModal(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    RethinkModalBottomSheet(
        onDismissRequest = onDismissRequest,
        contentPadding = PaddingValues(0.dp),
        verticalSpacing = 0.dp,
        includeBottomSpacer = false,
        content = content
    )
}

@Composable
fun RuleSheetLayout(
    modifier: Modifier = Modifier,
    bottomPadding: Dp,
    verticalSpacing: Dp = Dimensions.spacingMd,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        content()
    }
}

@Composable
fun rememberRuleSheetChipColors(): RuleSheetChipColors {
    return RuleSheetChipColors(
        neutralText = MaterialTheme.colorScheme.onSurfaceVariant,
        neutralBg = MaterialTheme.colorScheme.surfaceVariant,
        negativeText = MaterialTheme.colorScheme.error,
        negativeBg = MaterialTheme.colorScheme.errorContainer,
        positiveText = MaterialTheme.colorScheme.tertiary,
        positiveBg = MaterialTheme.colorScheme.tertiaryContainer
    )
}

@Composable
fun RuleSheetAppHeader(
    appName: String?,
    appIcon: Drawable?,
    modifier: Modifier = Modifier
) {
    if (appName.isNullOrBlank()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimensions.screenPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        appIcon?.let { icon ->
            val painter = rememberDrawablePainter(icon)
            if (painter != null) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.iconSizeSm)
                )
                Spacer(modifier = Modifier.width(Dimensions.spacingSmMd))
            }
        }
        Text(
            text = appName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun TrustBlockToggleStrip(
    isTrustSelected: Boolean,
    isBlockSelected: Boolean,
    onTrustClick: () -> Unit,
    onBlockClick: () -> Unit,
    iconSize: Dp = 28.dp,
    spacingBefore: Dp = Dimensions.spacingLg,
    spacingBetween: Dp = Dimensions.spacingMd
) {
    val trustIcon = if (isTrustSelected) R.drawable.ic_trust_accent else R.drawable.ic_trust
    val blockIcon = if (isBlockSelected) R.drawable.ic_block_accent else R.drawable.ic_block

    Spacer(modifier = Modifier.width(spacingBefore))
    Icon(
        painter = painterResource(id = trustIcon),
        contentDescription = null,
        modifier = Modifier.size(iconSize).clickable(onClick = onTrustClick)
    )
    Spacer(modifier = Modifier.width(spacingBetween))
    Icon(
        painter = painterResource(id = blockIcon),
        contentDescription = null,
        modifier = Modifier.size(iconSize).clickable(onClick = onBlockClick)
    )
}

@Composable
fun RuleSheetSectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimensions.screenPaddingHorizontal)
    )
}

@Composable
fun RuleSheetSupportingText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimensions.screenPaddingHorizontal)
    )
}

@Composable
fun RuleSheetDeleteAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimensions.screenPaddingHorizontal),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(text = stringResource(R.string.lbl_delete))
        }
    }
}

@Composable
fun RuleSheetSelectionValue(
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium
) {
    SelectionContainer(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.screenPaddingHorizontal)
        )
    }
}

@Composable
fun RuleSheetTrustBlockRow(
    value: String,
    isTrustSelected: Boolean,
    isBlockSelected: Boolean,
    onTrustClick: () -> Unit,
    onBlockClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueTextStyle: TextStyle = MaterialTheme.typography.titleMedium
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimensions.screenPaddingHorizontal,
                    vertical = Dimensions.spacingSm
                ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = valueTextStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TrustBlockToggleStrip(
            isTrustSelected = isTrustSelected,
            isBlockSelected = isBlockSelected,
            onTrustClick = onTrustClick,
            onBlockClick = onBlockClick
        )
    }
}

@Composable
fun RuleSheetChipOptionsRow(
    options: List<RuleSheetChipOption>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimensions.screenPaddingHorizontal),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSm)
    ) {
        options.forEach { option ->
            Box(modifier = Modifier.weight(1f).widthIn(min = 0.dp)) {
                RuleSheetFilterChip(
                    label = option.label,
                    selected = option.selected,
                    selectedText = option.selectedText,
                    selectedContainer = option.selectedContainer
                ) {
                    option.onClick()
                }
            }
        }
    }
}

@Composable
fun RuleSheetDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    RethinkConfirmDialog(
        onDismissRequest = onDismiss,
        title = title,
        message = message,
        confirmText = stringResource(R.string.lbl_delete),
        dismissText = stringResource(R.string.lbl_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isConfirmDestructive = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleSheetFilterChip(
    label: String,
    selected: Boolean,
    selectedText: Color,
    selectedContainer: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    RethinkFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        selectedLabelColor = selectedText,
        selectedContainerColor = selectedContainer,
        modifier = modifier,
        minHeight = Dimensions.touchTargetSm
    )
}

@Composable
fun RuleSheetModeToggle(
    autoLabel: String,
    manualLabel: String,
    isAutoSelected: Boolean,
    onAutoClick: () -> Unit,
    onManualClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RethinkTwoOptionSegmentedRow(
        leftLabel = autoLabel,
        rightLabel = manualLabel,
        leftSelected = isAutoSelected,
        onLeftClick = onAutoClick,
        onRightClick = onManualClick,
        modifier = modifier,
        minHeight = Dimensions.touchTargetSm
    )
}

suspend fun fetchRuleSheetAppIdentity(
    context: Context,
    uid: Int
): Pair<List<String>, Drawable?> {
    val appNames = FirewallManager.getAppNamesByUid(uid)
    val packageName = appNames.firstOrNull()?.let { FirewallManager.getPackageNameByAppName(it) }
    val icon =
        if (packageName.isNullOrEmpty()) {
            null
        } else {
            Utilities.getIcon(context, packageName)
        }

    return appNames to icon
}

fun formatRuleSheetAppName(context: Context, appNames: List<String>): String? {
    return when {
        appNames.isEmpty() -> null
        appNames.size >= 2 ->
            context.resources.getString(
                R.string.ctbs_app_other_apps,
                appNames[0],
                appNames.size.minus(1).toString()
            )
        else -> appNames[0]
    }
}

fun formatCustomRuleSheetAppName(context: Context, uid: Int, appNames: List<String>): String {
    return when {
        uid == UID_EVERYBODY ->
            context.resources.getString(R.string.firewall_act_universal_tab).replaceFirstChar(Char::titlecase)
        appNames.isEmpty() ->
            context.resources.getString(R.string.network_log_app_name_unknown) + " ($uid)"
        appNames.size >= 2 ->
            context.resources.getString(
                R.string.ctbs_app_other_apps,
                appNames[0],
                appNames.size.minus(1).toString()
            )
        else -> appNames[0]
    }
}

fun logFirewallRuleChange(
    eventLogger: EventLogger,
    title: String,
    details: String,
    tag: String? = null
) {
    eventLogger.log(
        EventType.FW_RULE_MODIFIED,
        Severity.LOW,
        title,
        EventSource.UI,
        false,
        details
    )
    tag?.let { Napier.v("$it $details") }
}

fun <T> launchRuleMutation(
    scope: CoroutineScope,
    mutation: suspend () -> T,
    onUpdated: (T) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        val result = mutation()
        withContext(Dispatchers.Main) {
            onUpdated(result)
        }
    }
}
