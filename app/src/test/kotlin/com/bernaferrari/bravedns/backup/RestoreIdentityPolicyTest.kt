package com.bernaferrari.bravedns.backup

import android.app.Application
import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.bernaferrari.bravedns.database.AppDatabase
import com.bernaferrari.bravedns.database.SubscriptionStatus
import com.bernaferrari.bravedns.service.PersistentState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.NATIVE)
class RestoreIdentityPolicyTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun `restore preserves settings but not device keys or enabled proxy state`() {
        val values = RestoreIdentityPolicy.preferences(mapOf(
            "ordinary" to true, "ANDROIDX.SECURITY.keyset" to "old", "master_key" to "old",
            PersistentState.USE_RPN to 1
        ))
        assertTrue(values["ordinary"] == true)
        assertFalse(values.containsKey("ANDROIDX.SECURITY.keyset"))
        assertFalse(values.containsKey("master_key"))
        assertEquals(0, values[PersistentState.USE_RPN])
    }

    @Test fun `actual Room purchase retains recovery token without imported authorization`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        fun openDb() = androidx.room3.Room.databaseBuilder(context, AppDatabase.DATABASE_NAME) {
            com.bernaferrari.bravedns.database.AppDatabaseConstructor.initialize()
        }.setDriver(AndroidSQLiteDriver()).build()
        val db = openDb()
        val purchase = SubscriptionStatus().apply {
            purchaseToken = "recoverable-token"; accountId = "account"; productId = "onetime.tier"
            developerPayload = "old-authorization"; sessionToken = "old-session"; deviceId = "old-device"
            status = 1; billingExpiry = Long.MAX_VALUE
        }
        db.subscriptionStatusDao().insert(purchase)
        db.close()
        val file = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        RestoreIdentityPolicy.clearImportedPurchases(file, AndroidSQLiteDriver())
        val restored = openDb()
        try {
            val row = requireNotNull(restored.subscriptionStatusDao().getSubscriptionByToken("recoverable-token"))
            assertEquals("account", row.accountId)
            assertEquals("onetime.tier", row.productId)
            assertEquals(0, row.status)
            assertEquals("", row.deviceId)
            assertEquals("", row.sessionToken)
            assertEquals("", row.developerPayload)
        } finally { restored.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }

    @Test fun `cleanup failure rolls back earlier changes in staged database`() {
        val file = File(directory.root, "incomplete.db")
        AndroidSQLiteDriver().open(file.path).use { db ->
            db.prepare("CREATE TABLE SubscriptionStatus(deviceId TEXT, sessionToken TEXT, developerPayload TEXT, status INTEGER)").use { it.step() }
            db.prepare("INSERT INTO SubscriptionStatus VALUES('old','old','old',1)").use { it.step() }
        }
        assertThrows(Exception::class.java) { RestoreIdentityPolicy.clearImportedPurchases(file, AndroidSQLiteDriver()) }
        AndroidSQLiteDriver().open(file.path).use { db ->
            db.prepare("SELECT status, deviceId FROM SubscriptionStatus").use {
                assertTrue(it.step()); assertEquals(1L, it.getLong(0)); assertEquals("old", it.getText(1))
            }
        }
    }
}
