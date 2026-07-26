/* Copyright 2026 RethinkDNS and its authors */

package com.bernaferrari.bravedns.ui.compose.logs

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

/** Portable identity for the value used to scope a domain-connections query. */
enum class DomainConnectionsInputType(val type: Int) {
    DOMAIN(0),
    FLAG(1),
    ASN(2),
    IP(3);

    companion object {
        fun fromValue(value: Int): DomainConnectionsInputType = entries.firstOrNull { it.type == value } ?: DOMAIN
    }
}

/** Target-neutral chrome for a filtered connection history. Data paging is owned by the host. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkDomainConnectionsScreen(
    title: String,
    subtitle: String,
    emptyMessage: String,
    isEmpty: Boolean,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    connectionsContent: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            RethinkLargeTopBar(
                title = title,
                subtitle = subtitle,
                onBackClick = onBackClick,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            connectionsContent()
            if (isEmpty) RethinkDomainConnectionsEmptyState(emptyMessage)
        }
    }
}

@Composable
private fun RethinkDomainConnectionsEmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SharedDimensions.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    MaterialSymbols.Filled.Public,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = SharedDimensions.spacingLg),
        )
    }
}
