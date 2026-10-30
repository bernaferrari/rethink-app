package com.bernaferrari.bravedns.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.paging.AsyncPagingDataDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.bernaferrari.bravedns.database.ConsoleLog
import com.bernaferrari.bravedns.database.ConsoleLogDatabase
import com.bernaferrari.bravedns.database.ConsoleLogDatabaseConstructor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ConsoleLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var database: ConsoleLogDatabase
    private lateinit var viewModel: ConsoleLogViewModel
    private lateinit var differ: AsyncPagingDataDiffer<ConsoleLog>
    private val store = ViewModelStore()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>()) {
            ConsoleLogDatabaseConstructor.initialize()
        }.setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(dispatcher).build()
        viewModel = ConsoleLogViewModel(database.consoleLogDAO())
        store.put("console", viewModel)
        differ = AsyncPagingDataDiffer(object : DiffUtil.ItemCallback<ConsoleLog>() {
            override fun areItemsTheSame(old: ConsoleLog, new: ConsoleLog) = old.id == new.id
            override fun areContentsTheSame(old: ConsoleLog, new: ConsoleLog) = old == new
        }, object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) = Unit
            override fun onRemoved(position: Int, count: Int) = Unit
            override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
            override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
        }, dispatcher, dispatcher)
    }

    @After fun cleanup() { store.clear(); database.close(); Dispatchers.resetMain() }

    @Test fun severityChangesActuallyRefilterExistingRowsAndComposeWithLiteralSearch() = consoleTest {
        database.consoleLogDAO().insertBatch(listOf(row("keep 100%_\\", 2), row("KEEP 100%_\\", 6), row("keep 100xxx", 6)))
        collect()
        viewModel.setFilter("keep 100%_\\")
        viewModel.setLogLevel(5)
        advanceUntilIdle()
        assertEquals(listOf("KEEP 100%_\\"), messages())
        viewModel.setLogLevel(0)
        advanceUntilIdle()
        assertEquals(listOf("KEEP 100%_\\", "keep 100%_\\"), messages())
    }

    @Test fun pauseFreezesTheWholeSnapshotButSearchAndSeverityRemainLiveUntilResume() = consoleTest {
        database.consoleLogDAO().insertBatch(listOf(row("old error", 6), row("old info", 3)))
        collect()
        viewModel.setFilter("info")
        viewModel.setPaused(true)
        advanceUntilIdle()
        assertTrue(viewModel.pauseState.value.paused)
        database.consoleLogDAO().insert(row("new error", 6))
        viewModel.setFilter("error")
        viewModel.setLogLevel(5)
        advanceUntilIdle()
        assertEquals(listOf("old error"), messages())
        viewModel.setPaused(false)
        advanceUntilIdle()
        assertEquals(listOf("new error", "old error"), messages())
        assertFalse(viewModel.pauseState.value.paused)
    }

    @Test fun pausedSnapshotIsBoundedToNewest5000RowsAndSurvivesDatabaseDeletion() = consoleTest {
        database.consoleLogDAO().insertBatch((1..5002).map { row("entry $it", 5) })
        collect()
        viewModel.setPaused(true)
        advanceUntilIdle()
        assertEquals(5000, messages().size)
        assertEquals("entry 5002", messages().first())
        assertEquals("entry 3", messages().last())
        database.consoleLogDAO().deleteAllLogs()
        viewModel.setFilter("entry 5002")
        advanceUntilIdle()
        assertEquals(listOf("entry 5002"), messages())
        viewModel.setPaused(false)
        advanceUntilIdle()
        assertTrue(messages().isEmpty())
    }

    @Test fun unicodeCaseAndLiteralSearchHaveTheSameSemanticsLiveAndPaused() = consoleTest {
        database.consoleLogDAO().insertBatch(listOf(row("Ärger 100%_\\", 5), row("ärger 100%_\\", 5)))
        collect()
        viewModel.setFilter("ÄRGER 100%_\\")
        advanceUntilIdle()
        val live = messages()
        assertEquals(listOf("Ärger 100%_\\"), live)
        viewModel.setPaused(true)
        advanceUntilIdle()
        assertEquals(live, messages())
    }

    @Test fun globMetacharactersAreLiteralLiveAndPaused() = consoleTest {
        database.consoleLogDAO().insertBatch(listOf(row("literal [a-z]*?", 5), row("literal zXYZ", 5)))
        collect()
        viewModel.setFilter("[a-z]*?")
        advanceUntilIdle()
        assertEquals(listOf("literal [a-z]*?"), messages())
        viewModel.setPaused(true)
        advanceUntilIdle()
        assertEquals(listOf("literal [a-z]*?"), messages())
        viewModel.setFilter("")
        advanceUntilIdle()
        val allRows = listOf("literal zXYZ", "literal [a-z]*?")
        assertEquals(allRows, messages())
        viewModel.setPaused(false)
        advanceUntilIdle()
        assertEquals(allRows, messages())
    }

    @Test fun explicitUserClearDropsFrozenCopiesWithoutResumingLiveUpdates() = consoleTest {
        database.consoleLogDAO().insert(row("private old log", 5))
        collect()
        viewModel.setPaused(true)
        advanceUntilIdle()
        assertEquals(listOf("private old log"), messages())
        database.consoleLogDAO().deleteAllLogs()
        viewModel.onLogsCleared()
        database.consoleLogDAO().insert(row("new background log", 5))
        advanceUntilIdle()
        assertTrue(messages().isEmpty())
        assertTrue(viewModel.pauseState.value.paused)
        viewModel.setPaused(false)
        advanceUntilIdle()
        assertEquals(listOf("new background log"), messages())
    }

    @Test fun failedPauseReturnsToLiveAndExposesAnActionableError() = consoleTest {
        database.consoleLogDAO().insert(row("before close", 5))
        database.close()
        viewModel.setPaused(true)
        advanceUntilIdle()
        assertFalse(viewModel.pauseState.value.paused)
        assertFalse(viewModel.pauseState.value.loading)
        assertNotNull(viewModel.pauseState.value.error)
    }

    @Test fun resumeDuringSnapshotLoadCancelsTheReadAndCannotPublishLatePausedState() = consoleTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<List<ConsoleLog>>()
        val dao = object : com.bernaferrari.bravedns.database.ConsoleLogDAO by database.consoleLogDAO() {
            override suspend fun newestLogs(limit: Int): List<ConsoleLog> { entered.complete(Unit); return release.await() }
        }
        val pending = ConsoleLogViewModel(dao)
        store.put("pending", pending)
        pending.setPaused(true)
        runCurrent()
        assertTrue(entered.isCompleted)
        assertTrue(pending.pauseState.value.loading)
        pending.setPaused(false)
        release.complete(listOf(row("late", 5)))
        advanceUntilIdle()
        assertFalse(pending.pauseState.value.paused)
        assertFalse(pending.pauseState.value.loading)
        assertNull(pending.pauseState.value.error)
    }

    private val collectors = mutableListOf<Job>()
    private fun consoleTest(block: suspend TestScope.() -> Unit) = runTest {
        try { block() } finally { collectors.forEach { it.cancelAndJoin() } }
    }
    private fun TestScope.collect() {
        collectors += launch { viewModel.logs.collectLatest { differ.submitData(it) } }
    }
    private fun messages() = differ.snapshot().items.map { it.message }
    private fun row(message: String, level: Long) = ConsoleLog(message = message, level = level, timestamp = 1)
}
