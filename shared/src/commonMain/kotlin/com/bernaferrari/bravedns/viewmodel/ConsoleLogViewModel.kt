/* Copyright 2024 RethinkDNS and its authors */
package com.bernaferrari.bravedns.viewmodel

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Provided
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.sqlite.SQLiteException
import com.bernaferrari.bravedns.database.ConsoleLog
import com.bernaferrari.bravedns.database.ConsoleLogDAO
import com.bernaferrari.bravedns.util.Constants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Search and severity are one reactive query, shared by live and frozen views. */
internal data class ConsoleLogQuery(val text: String = "", val minLevel: Long = 0) {
    // GLOB has deterministic case-sensitive Unicode semantics even on ICU-enabled Android SQLite.
    // Expand ASCII letters explicitly and escape every GLOB metacharacter for literal search.
    val sqlPattern: String get() = buildString {
        append('*')
        text.forEach { char ->
            when (char) {
                in 'a'..'z' -> append("[$char${char - ('a' - 'A')}]")
                in 'A'..'Z' -> append("[${char + ('a' - 'A')}$char]")
                '*' -> append("[*]")
                '?' -> append("[?]")
                '[' -> append("[[]")
                ']' -> append("[]]")
                else -> append(char)
            }
        }
        append('*')
    }
    private val foldedNeedle = asciiFold(text)
    // Match the SQL pattern's explicit ASCII folding without changing Unicode case.
    fun matches(log: ConsoleLog): Boolean = log.level >= minLevel && (text.isEmpty() || asciiFold(log.message).contains(foldedNeedle))

    private fun asciiFold(value: String): String = buildString(value.length) {
        value.forEach { append(if (it in 'A'..'Z') it + ('a' - 'A') else it) }
    }
}

data class ConsolePauseState(val paused: Boolean = false, val loading: Boolean = false, val error: Exception? = null)

@KoinViewModel
class ConsoleLogViewModel(@Provided private val dao: ConsoleLogDAO) : ViewModel() {
    private val query = MutableStateFlow(ConsoleLogQuery())
    private val snapshot = MutableStateFlow<List<ConsoleLog>?>(null)
    private val pause = MutableStateFlow(ConsolePauseState())
    val pauseState = pause.asStateFlow()
    private var pauseJob: Job? = null
    private var generation = 0

    val pagingConfig = PagingConfig(pageSize = Constants.PAGING_PAGE_SIZE, enablePlaceholders = false, prefetchDistance = 10)

    @OptIn(ExperimentalCoroutinesApi::class)
    val logs: Flow<PagingData<ConsoleLog>> = combine(query, snapshot) { search, frozen -> search to frozen }
        .flatMapLatest { (search, frozen) ->
            if (frozen == null) Pager(pagingConfig) { dao.getLogs(search.sqlPattern, search.minLevel) }.flow
            else flowOf(PagingData.from(frozen.filter(search::matches)))
        }.cachedIn(viewModelScope)

    suspend fun sinceTime(): Long = dao.sinceTime()
    fun setLogLevel(level: Long) { query.value = query.value.copy(minLevel = level) }
    fun setFilter(filter: String) { query.value = query.value.copy(text = filter) }

    /** Freeze the newest bounded rows, regardless of current filters, so paused search remains useful. */
    fun setPaused(paused: Boolean) {
        if (paused && (pause.value.paused || pause.value.loading)) return
        val request = ++generation
        pauseJob?.cancel()
        if (!paused) {
            snapshot.value = null
            pause.value = ConsolePauseState()
            return
        }
        pause.value = ConsolePauseState(loading = true)
        pauseJob = viewModelScope.launch {
            try {
                val rows = dao.newestLogs(MAX_PAUSED_ROWS).take(MAX_PAUSED_ROWS).map { it.copy() }
                if (request == generation) {
                    snapshot.value = rows
                    pause.value = ConsolePauseState(paused = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SQLiteException) {
                failedPause(request, error)
            } catch (error: IllegalStateException) {
                failedPause(request, error)
            }
        }
    }

    /** An explicit user deletion clears private frozen copies too; background eviction does not. */
    fun onLogsCleared() {
        generation++
        pauseJob?.cancel()
        val keepPaused = pause.value.paused || pause.value.loading
        snapshot.value = if (keepPaused) emptyList() else null
        pause.value = ConsolePauseState(paused = keepPaused)
    }

    private fun failedPause(request: Int, error: Exception) {
        if (request == generation) pause.value = ConsolePauseState(error = error)
    }

    companion object { const val MAX_PAUSED_ROWS = 5_000 }
}
