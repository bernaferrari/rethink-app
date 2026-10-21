/* Copyright 2026 RethinkDNS and its authors */
package com.bernaferrari.bravedns.ui.compose.dns

import com.bernaferrari.bravedns.ui.icons.MaterialSymbols

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.bernaferrari.bravedns.ui.compose.common.RethinkEmptyState
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkLargeTopBar
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkTopBar
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions

/** A small bridge keeps Paging Android-only while every endpoint-list visual is portable. */
interface RethinkEndpointFeed<T> {
    val itemCount: Int
    operator fun get(index: Int): T?
}

data class RethinkInMemoryEndpointFeed<T>(val items: List<T>) : RethinkEndpointFeed<T> {
    override val itemCount: Int get() = items.size
    override fun get(index: Int): T? = items.getOrNull(index)
}

/** Shared page frame for a concrete DNS transport's endpoint list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RethinkEndpointConfigurationScaffold(
    title: String,
    subtitle: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RethinkLargeTopBar(title = title, subtitle = subtitle, onBackClick = onBackClick, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = SharedDimensions.screenPaddingHorizontal),
        ) {
            content(PaddingValues(vertical = SharedDimensions.spacingXs))
        }
    }
}

@Composable
fun <T> RethinkEndpointListWithAdd(
    feed: RethinkEndpointFeed<T>,
    createLabel: String,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(SharedDimensions.spacingNone),
    emptyTitle: String = createLabel,
    emptyMessage: String = "",
    itemContent: @Composable (T) -> Unit,
) {
    Box(modifier.fillMaxSize().padding(contentPadding)) {
        if (feed.itemCount == 0) {
            RethinkEmptyState(
                title = emptyTitle,
                message = emptyMessage,
                icon = MaterialSymbols.Filled.Dns,
                ctaLabel = createLabel,
                onCtaClick = onCreate,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Clear the extended FAB so the last endpoint is never hidden under it.
                contentPadding = PaddingValues(
                    top = SharedDimensions.spacingXs,
                    bottom = SharedDimensions.buttonHeightLg + SharedDimensions.spacing2xl,
                ),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                items(feed.itemCount) { index ->
                    val item = feed[index] ?: return@items
                    itemContent(item)
                }
            }
            ExtendedFloatingActionButton(
                onClick = onCreate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(SharedDimensions.spacingLg),
                elevation = FloatingActionButtonDefaults.loweredElevation(),
            ) {
                Icon(MaterialSymbols.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(SharedDimensions.spacingSm))
                Text(createLabel)
            }
        }
    }
}


/** Full-page endpoint picker used for DNSCrypt relay configuration. */
@Composable
fun <T> RethinkEndpointPicker(
    title: String,
    feed: RethinkEndpointFeed<T>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RethinkTopBar(title = title, onBackClick = onBackClick) },
    ) { padding ->
        if (feed.itemCount == 0) {
            RethinkEmptyState(
                title = title,
                message = "",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = SharedDimensions.screenPaddingHorizontal, vertical = SharedDimensions.spacingXs),
                verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingXs),
            ) {
                items(feed.itemCount) { index ->
                    val item = feed[index] ?: return@items
                    itemContent(item)
                }
            }
        }
    }
}

/** Shared DNSCrypt resolver heading and relay-config shortcut. */
@Composable
fun RethinkDnsCryptRelayShortcut(
    sectionTitle: String,
    relayTitle: String,
    configureLabel: String,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHeader(sectionTitle)
        RethinkListGroup {
            RethinkListItem(
                headline = relayTitle,
                position = CardPosition.Single,
                showTrailingChevron = true,
                onClick = onConfigure,
                trailing = {
                    Text(configureLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                },
            )
        }
    }
}
