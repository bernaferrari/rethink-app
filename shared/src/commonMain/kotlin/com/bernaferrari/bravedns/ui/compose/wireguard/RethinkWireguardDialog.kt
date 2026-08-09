/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.wireguard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

/** Responsive shared dialog shell used by WireGuard editing flows. */
@Composable
fun RethinkWireguardDialog(
    onDismissRequest: () -> Unit,
    useSurface: Boolean = false,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wideWindow = maxWidth >= 600.dp
            val contentModifier = if (wideWindow) {
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SharedDimensions.cornerRadius4xl))
            } else {
                Modifier.fillMaxWidth()
            }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                if (useSurface) {
                    Surface(
                        modifier = contentModifier,
                        shape = if (wideWindow) RoundedCornerShape(SharedDimensions.cornerRadius4xl) else androidx.compose.ui.graphics.RectangleShape,
                        color = MaterialTheme.colorScheme.background,
                        content = content,
                    )
                } else {
                    Box(modifier = contentModifier) { content() }
                }
            }
        }
    }
}

@Composable
fun RethinkWireguardDialogColumn(
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = SharedDimensions.spacingMd,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier.fillMaxWidth()
                .padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingLg)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}
