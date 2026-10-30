/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.common.RethinkWindowBreakpoints
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

/**
 * Portable visual shell for the platform-owned biometric prompt. It echoes Home's protection mark
 * (the coral seal on a soft halo) holding a lock, says once what is asked, and keeps the one
 * action at the bottom, within thumb reach.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RethinkAppLockContent(
    appName: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    unlockLabel: String? = null,
    onUnlockClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = scheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(LockMarkSize).semantics { contentDescription = appName },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(LockMarkSize)
                        .rotate(HALO_ANGLE)
                        .background(scheme.primaryContainer.copy(alpha = 0.55f), MaterialShapes.SoftBurst.toShape()),
                )
                Box(
                    Modifier
                        .size(LockMarkSize * 0.72f)
                        .background(scheme.primary, MaterialShapes.Cookie12Sided.toShape()),
                )
                Icon(MaterialSymbols.Filled.Lock, null, tint = scheme.onPrimary, modifier = Modifier.size(LockMarkSize * 0.3f))
            }
            Spacer(Modifier.height(SharedDimensions.spacing2xl))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(SharedDimensions.spacingSm))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
            Spacer(Modifier.weight(1.2f))
            if (onUnlockClick != null) {
                val height = ButtonDefaults.MediumContainerHeight
                Button(
                    onClick = onUnlockClick,
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height),
                    modifier = Modifier
                        .widthIn(max = RethinkWindowBreakpoints.ContentMax)
                        .fillMaxWidth()
                        .heightIn(min = height),
                ) {
                    Icon(MaterialSymbols.Filled.Lock, null, Modifier.size(ButtonDefaults.iconSizeFor(height)))
                    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
                    Text(unlockLabel ?: title, style = ButtonDefaults.textStyleFor(height))
                }
            }
        }
    }
}

private val LockMarkSize = SharedDimensions.brandMarkSize
private const val HALO_ANGLE = 15f
