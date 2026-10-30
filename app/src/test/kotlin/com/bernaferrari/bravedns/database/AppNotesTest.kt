package com.bernaferrari.bravedns.database

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppNotesTest {
    @Test fun notesAreScopedToPackageEvenWhenUidIsShared() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>()) {
            AppDatabaseConstructor.initialize()
        }.setDriver(BundledSQLiteDriver()).build()
        try {
            val dao = db.appInfoDAO()
            dao.insert(AppInfo(packageName = "first", uid = 10001))
            dao.insert(AppInfo(packageName = "second", uid = 10001))
            val repository = AppInfoRepository(dao)
            assertTrue(repository.updateNotes(10001, "first", "Trusted for calls"))
            assertEquals("Trusted for calls", dao.isUidPkgExist(10001, "first")!!.notes)
            assertEquals("", dao.isUidPkgExist(10001, "second")!!.notes)
            assertFalse(repository.updateNotes(10001, "missing", "not saved"))
        } finally { db.close() }
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedNotesAreRejectedBeforeWriting() = runBlocking {
        AppInfoRepository(io.mockk.mockk()).updateNotes(1, "first", "x".repeat(501))
        Unit
    }
}
