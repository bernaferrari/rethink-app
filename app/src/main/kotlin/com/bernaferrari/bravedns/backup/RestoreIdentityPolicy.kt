package com.bernaferrari.bravedns.backup

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.bernaferrari.bravedns.service.PersistentState
import java.io.File

/** Keep recovery metadata, but never trust another device's authorization after restore. */
internal object RestoreIdentityPolicy {
    fun preferences(values: Map<String, Any?>): Map<String, Any?> = values.filterKeys { key ->
        !key.contains("androidx.security", ignoreCase = true) &&
            !key.contains("keyset", ignoreCase = true) &&
            !key.contains("master_key", ignoreCase = true)
    } + (PersistentState.USE_RPN to 0)

    fun clearImportedPurchases(database: File, driver: SQLiteDriver? = null) {
        (driver ?: BundledSQLiteDriver()).open(database.path).use { db ->
            db.execute("BEGIN IMMEDIATE")
            try {
                // Play and website can reverify their retained purchase token. F-Droid
                // retains recovery metadata for a later channel switch, without access.
                db.execute("""UPDATE SubscriptionStatus SET deviceId = '', sessionToken = '',
                    developerPayload = '', status = 0""")
                db.execute("""UPDATE RpnProxy SET configPath = '', serverResPath = '',
                    tunId = '', isActive = 0""")
                db.execute("UPDATE ProxyApplicationMapping SET isActive = 0 WHERE proxyId LIKE '%rpn' OR proxyId = 'Auto' OR proxyId LIKE 'RPN-WIN%'")
                db.execute("COMMIT")
            } catch (error: SQLiteException) {
                db.execute("ROLLBACK")
                throw error
            }
        }
    }

    private fun SQLiteConnection.execute(sql: String) { prepare(sql).use { it.step() } }
}
