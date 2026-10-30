package com.bernaferrari.bravedns.database

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [28])
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
class SubscriptionTerminalTransitionTest {
    @Test fun terminalUpdatesAreTokenScopedAndHistoryIsIdempotent() = runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val database = androidx.room3.Room.databaseBuilder(context, AppDatabase.DATABASE_NAME) {
            AppDatabaseConstructor.initialize()
        }.setDriver(androidx.sqlite.driver.AndroidSQLiteDriver()).build()
        try {
            val dao = database.subscriptionStatusDao()
            val old = SubscriptionStatus().apply { purchaseToken = "old"; status = 1; billingExpiry = 9000 }
            val current = SubscriptionStatus().apply { purchaseToken = "current"; status = 1; billingExpiry = 10000 }
            dao.insert(old)
            dao.insert(current)
            assertTrue(dao.transitionTerminalPurchase("old", 7, 100))
            assertFalse(dao.transitionTerminalPurchase("old", 2, 200))
            assertFalse(dao.transitionTerminalPurchase("old", 7, 300))
            assertEquals(7, dao.getSubscriptionByToken("old")?.status)
            assertEquals(100L, dao.getSubscriptionByToken("old")?.billingExpiry)
            assertEquals(1, dao.getSubscriptionByToken("current")?.status)
            assertEquals(1, database.subscriptionStateHistoryDao().getRecentHistory().size)
        } finally { database.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }
}
