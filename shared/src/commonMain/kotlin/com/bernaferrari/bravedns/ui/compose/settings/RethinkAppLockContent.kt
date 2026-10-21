/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

/** Portable visual shell for the platform-owned biometric authentication prompt. */
@Composable
fun RethinkAppLockContent(
    appName: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    unlockLabel: String? = null,
    onUnlockClick: (() -> Unit)? = null,
    brandMark: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Identity first (mark + name), then what is asked of the user, then the one action.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(SharedDimensions.cornerRadius2xl),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SharedDimensions.spacingXl, vertical = SharedDimensions.spacing2xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(SharedDimensions.brandMarkSize), contentAlignment = Alignment.Center) { brandMark() }
                    Spacer(Modifier.height(SharedDimensions.spacingLg))
                    Text(appName, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(SharedDimensions.spacingSm))
                    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(SharedDimensions.spacingXs))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (onUnlockClick != null) {
                Spacer(Modifier.height(SharedDimensions.spacingXl))
                Button(
                    onClick = onUnlockClick,
                    modifier = Modifier.fillMaxWidth().height(SharedDimensions.buttonHeight),
                    shape = RoundedCornerShape(SharedDimensions.buttonCornerRadius),
                ) {
                    Text(unlockLabel ?: title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
