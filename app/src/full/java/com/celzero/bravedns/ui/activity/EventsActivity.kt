/*
 * Copyright 2025 RethinkDNS and its authors
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
package com.celzero.bravedns.ui.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.adapter.EventsAdapter
import com.celzero.bravedns.database.EventDao
import com.celzero.bravedns.database.EventSource
import com.celzero.bravedns.database.Severity
import com.celzero.bravedns.ui.compose.theme.RethinkTheme
import com.celzero.bravedns.util.Themes
import com.celzero.bravedns.util.UIUtils.formatToRelativeTime
import com.celzero.bravedns.util.Utilities.isAtleastQ
import com.celzero.bravedns.util.handleFrostEffectIfNeeded
import com.celzero.bravedns.util.restoreFrost
import com.celzero.bravedns.viewmodel.EventsViewModel
import com.celzero.bravedns.viewmodel.EventsViewModel.TopLevelFilter
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class EventsActivity : AppCompatActivity(), SearchView.OnQueryTextListener {
    private val persistentState by inject<com.celzero.bravedns.service.PersistentState>()
    private val viewModel: EventsViewModel by viewModel()
    private val eventDao by inject<EventDao>()

    private var layoutManager: RecyclerView.LayoutManager? = null
    private var recyclerAdapter: EventsAdapter? = null
    private var recyclerViewRef: FastScrollRecyclerView? = null
    private var searchViewRef: SearchView? = null

    private var filterQuery: String = ""
    private val searchQuery = MutableStateFlow("")
    private var filterSources by mutableStateOf(setOf<EventSource>())
    private var filterSeverity by mutableStateOf<Severity?>(null)
    private var filterType by mutableStateOf(TopLevelFilter.ALL)

    private var showSeverityChips by mutableStateOf(false)
    private var showSourceChips by mutableStateOf(false)
    private var showEmptyState by mutableStateOf(false)
    private var scrollHeaderText by mutableStateOf("")
    private var scrollHeaderVisible by mutableStateOf(false)

    companion object {
        private const val TAG = "EventsActivity"
        private const val QUERY_TEXT_DELAY: Long = 1000
    }

    private fun Context.isDarkThemeOn(): Boolean {
        return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        theme.applyStyle(Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme), true)
        super.onCreate(savedInstanceState)

        handleFrostEffectIfNeeded(persistentState.theme)

        if (isAtleastQ()) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightNavigationBars = false
            window.isNavigationBarContrastEnforced = false
        }

        initView()

        setContent {
            RethinkTheme {
                EventsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val themeId = Themes.getCurrentTheme(isDarkThemeOn(), persistentState.theme)
        restoreFrost(themeId)

        searchViewRef?.clearFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        searchViewRef?.let { imm.restartInput(it) }
        recyclerViewRef?.requestFocus()
    }

    private fun initView() {
        setupRecyclerView()
        setQueryFilter()
    }

    private fun setupRecyclerView() {
        layoutManager = LinearLayoutManager(this)
        val adapter = EventsAdapter(this)
        recyclerAdapter = adapter

        viewModel.eventsList.observe(this) { pagingData ->
            adapter.submitData(lifecycle, pagingData)
        }

        adapter.addLoadStateListener { loadState ->
            val isEmpty = adapter.itemCount < 1
            if (loadState.append.endOfPaginationReached && isEmpty) {
                showEmptyState = true
            } else {
                showEmptyState = false
            }
        }
    }

    private fun setupRecyclerScrollListener() {
        recyclerViewRef?.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val firstChild = recyclerView.getChildAt(0) ?: return
                    val tag = firstChild.tag as? Long ?: return
                    scrollHeaderText = formatToRelativeTime(this@EventsActivity, tag)
                    scrollHeaderVisible = true
                }
            }
        )
    }

    @OptIn(FlowPreview::class)
    private fun setQueryFilter() {
        lifecycleScope.launch {
            searchQuery
                .debounce(QUERY_TEXT_DELAY)
                .distinctUntilChanged()
                .collect { query ->
                    filterQuery = query
                    viewModel.setFilter(query, filterSources, filterSeverity)
                }
        }
    }

    private fun refreshEvents() {
        viewModel.setFilter(filterQuery, filterSources, filterSeverity)
    }

    private fun showDeleteDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.App_Dialog_NoDim)
            .setTitle(R.string.ada_delete_logs_dialog_title)
            .setMessage(R.string.ada_delete_logs_dialog_desc)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.lbl_delete)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) { eventDao.deleteAll() }
                refreshEvents()
            }
            .setNegativeButton(getString(R.string.lbl_cancel)) { _, _ -> }
            .create()
            .show()
    }

    private fun applyFilter(tag: TopLevelFilter) {
        filterType = tag
        when (tag) {
            TopLevelFilter.ALL -> {
                filterSeverity = null
                filterSources = emptySet()
                viewModel.setFilter(filterQuery, emptySet(), null)
            }
            TopLevelFilter.SOURCE -> {
                filterSeverity = null
                if (filterSources.isEmpty()) {
                    viewModel.setFilter(filterQuery, emptySet(), null)
                } else {
                    viewModel.setFilter(filterQuery, filterSources, null)
                }
            }
            TopLevelFilter.SEVERITY -> {
                filterSources = emptySet()
                viewModel.setFilter(filterQuery, emptySet(), filterSeverity)
            }
        }
    }

    private fun onSeveritySelected(severity: Severity?) {
        if (severity == null) {
            filterSeverity = null
            filterSources = emptySet()
            filterType = TopLevelFilter.ALL
            viewModel.setFilter(filterQuery, emptySet(), null)
            return
        }
        filterSeverity = severity
        filterSources = emptySet()
        filterType = TopLevelFilter.SEVERITY
        viewModel.setFilter(filterQuery, emptySet(), filterSeverity)
    }

    private fun toggleSource(source: EventSource) {
        filterSources =
            if (filterSources.contains(source)) {
                filterSources - source
            } else {
                filterSources + source
            }

        if (filterSources.isEmpty()) {
            filterType = TopLevelFilter.ALL
            filterSeverity = null
            viewModel.setFilter(filterQuery, emptySet(), null)
        } else {
            filterType = TopLevelFilter.SOURCE
            filterSeverity = null
            viewModel.setFilter(filterQuery, filterSources, null)
        }
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        searchQuery.value = query
        return true
    }

    override fun onQueryTextChange(query: String): Boolean {
        searchQuery.value = query
        return true
    }

    @Composable
    private fun EventsScreen() {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchRow(
                    onFilterClick = {
                        showSeverityChips = !showSeverityChips
                        if (!showSeverityChips) {
                            showSourceChips = false
                        }
                    },
                    onRefreshClick = { refreshEvents() },
                    onDeleteClick = { showDeleteDialog() }
                )

                if (showSeverityChips) {
                    SeverityChips()
                    if (showSourceChips) {
                        SourceChips()
                    }
                }

                EventsList()
            }

            if (showEmptyState) {
                EmptyState()
            }

            if (scrollHeaderVisible) {
                Text(
                    text = scrollHeaderText,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 100.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            }
        }
    }

    @Composable
    private fun SearchRow(
        onFilterClick: () -> Unit,
        onRefreshClick: () -> Unit,
        onDeleteClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    SearchView(ctx).apply {
                        isIconified = false
                        queryHint = ctx.getString(R.string.search_event_logs)
                        setOnQueryTextListener(this@EventsActivity)
                        setOnSearchClickListener {
                            showSeverityChips = true
                        }
                        searchViewRef = this
                    }
                },
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onFilterClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_filter),
                    contentDescription = null
                )
            }

            IconButton(onClick = onRefreshClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh_white),
                    contentDescription = null
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null
                )
            }
        }
    }

    @Composable
    private fun SeverityChips() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                label = stringResourceCompat(R.string.lbl_all),
                selected = filterType == TopLevelFilter.ALL,
                onClick = {
                    showSourceChips = false
                    applyFilter(TopLevelFilter.ALL)
                }
            )
            FilterChip(
                label = stringResourceCompat(R.string.events_severity_low),
                selected = filterSeverity == Severity.LOW,
                onClick = {
                    showSourceChips = false
                    onSeveritySelected(Severity.LOW)
                }
            )
            FilterChip(
                label = stringResourceCompat(R.string.events_severity_medium),
                selected = filterSeverity == Severity.MEDIUM,
                onClick = {
                    showSourceChips = false
                    onSeveritySelected(Severity.MEDIUM)
                }
            )
            FilterChip(
                label = stringResourceCompat(R.string.events_severity_high),
                selected = filterSeverity == Severity.HIGH,
                onClick = {
                    showSourceChips = false
                    onSeveritySelected(Severity.HIGH)
                }
            )
            FilterChip(
                label = stringResourceCompat(R.string.events_severity_critical),
                selected = filterSeverity == Severity.CRITICAL,
                onClick = {
                    showSourceChips = false
                    onSeveritySelected(Severity.CRITICAL)
                }
            )
            FilterChip(
                label = stringResourceCompat(R.string.events_filter_source),
                selected = filterType == TopLevelFilter.SOURCE,
                onClick = {
                    showSourceChips = true
                    applyFilter(TopLevelFilter.SOURCE)
                }
            )
        }
    }

    @Composable
    private fun SourceChips() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EventSource.entries.forEach { source ->
                FilterChip(
                    label = sourceLabel(source),
                    selected = filterSources.contains(source),
                    onClick = { toggleSource(source) }
                )
            }
        }
    }

    @Composable
    private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(text = label) },
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }

    @Composable
    private fun EventsList() {
        val adapter = recyclerAdapter
        if (adapter == null) return
        AndroidView(
            factory = { ctx ->
                FastScrollRecyclerView(ctx).apply {
                    layoutManager = this@EventsActivity.layoutManager
                    this.adapter = adapter
                    if (recyclerViewRef == null) {
                        recyclerViewRef = this
                        setupRecyclerScrollListener()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    @Composable
    private fun EmptyState() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_event_note),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResourceCompat(R.string.no_events_recorded),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResourceCompat(R.string.no_events_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    private fun stringResourceCompat(id: Int): String {
        val context = LocalContext.current
        return context.getString(id)
    }

    private fun sourceLabel(source: EventSource): String {
        val text = source.name.lowercase().replace('_', ' ')
        return text.replaceFirstChar { it.titlecase() }
    }
}
