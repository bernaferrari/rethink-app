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
package com.celzero.bravedns.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for state transition history
 */
@Dao
interface SubscriptionStateHistoryDao {

    @Insert
    suspend fun insert(history: SubscriptionStateHistory): Long

    @Query("SELECT * FROM SubscriptionStateHistory WHERE subscriptionId = :subscriptionId ORDER BY timestamp DESC")
    suspend fun getHistoryForSubscription(subscriptionId: Int): List<SubscriptionStateHistory>

    @Query("SELECT * FROM SubscriptionStateHistory ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 100): List<SubscriptionStateHistory>

    /** All history entries ordered newest-first, as a PagingSource for the history screen. */
    @Query("SELECT * FROM SubscriptionStateHistory ORDER BY timestamp DESC")
    fun getAllHistoryPaged(): PagingSource<Int, SubscriptionStateHistory>

    /** All history as a Flow so the history screen can react to new inserts live. */
    @Query("SELECT * FROM SubscriptionStateHistory ORDER BY timestamp DESC")
    fun observeAllHistory(): Flow<List<SubscriptionStateHistory>>

    /**
     *
     * Applies the noise-filter rules defined at the top of this file:
     *  - same-state rows excluded
     *  - fromState = STATE_UNKNOWN(4) or negative (-1 sentinel) excluded
     *  - toState   = STATE_UNKNOWN(4) or STATE_INITIAL(0) excluded
     * Initial(0) → Active(1) is intentionally shown (fromState=0 is still a valid source).
     */
    @Query("""
        SELECT * FROM SubscriptionStateHistory
        WHERE fromState != toState
          AND fromState NOT IN (-1, 4)
          AND toState   NOT IN (0, 4)
        ORDER BY timestamp DESC
    """)
    fun observeHistoryPaged(): PagingSource<Int, SubscriptionStateHistory>

    @Query("""
        SELECT
            h.id          AS id,
            h.subscriptionId AS subscriptionId,
            h.fromState   AS fromState,
            h.toState     AS toState,
            h.timestamp   AS timestamp,
            h.reason      AS reason,
            COALESCE(s.productId,    '') AS productId,
            COALESCE(s.productTitle, '') AS productTitle,
            COALESCE(s.purchaseToken,'') AS purchaseToken,
            COALESCE(s.planId,       '') AS planId,
            COALESCE(s.purchaseTime, 0)  AS purchaseTime,
            COALESCE(s.billingExpiry, 0) AS billingExpiry,
            COALESCE(s.accountId,    '') AS accountId
        FROM SubscriptionStateHistory h
        LEFT JOIN SubscriptionStatus s ON h.subscriptionId = s.id
        ORDER BY h.timestamp DESC
    """)
    fun observeRichHistory(): Flow<List<RichHistoryEntry>>

    @Query("""
        SELECT
            h.id          AS id,
            h.subscriptionId AS subscriptionId,
            h.fromState   AS fromState,
            h.toState     AS toState,
            h.timestamp   AS timestamp,
            h.reason      AS reason,
            COALESCE(s.productId,    '') AS productId,
            COALESCE(s.productTitle, '') AS productTitle,
            COALESCE(s.purchaseToken,'') AS purchaseToken,
            COALESCE(s.planId,       '') AS planId,
            COALESCE(s.purchaseTime, 0)  AS purchaseTime,
            COALESCE(s.billingExpiry, 0) AS billingExpiry,
            COALESCE(s.accountId,    '') AS accountId
        FROM SubscriptionStateHistory h
        LEFT JOIN SubscriptionStatus s ON h.subscriptionId = s.id
        WHERE h.fromState != h.toState
          AND h.fromState NOT IN (-1, 4)
          AND h.toState   NOT IN (0, 4)
        ORDER BY h.timestamp DESC
    """)
    fun observeRichHistoryPaged(): PagingSource<Int, RichHistoryEntry>

    /** Total count of meaningful (non-noise) history entries shown to the user. */
    @Query("""
        SELECT COUNT(*) FROM SubscriptionStateHistory
        WHERE fromState != toState
          AND fromState NOT IN (-1, 4)
          AND toState   NOT IN (0, 4)
    """)
    suspend fun getMeaningfulCount(): Int

    @Query("DELETE FROM SubscriptionStateHistory WHERE timestamp < :cutoffTime")
    suspend fun deleteOldHistory(cutoffTime: Long): Int

    @Query("SELECT COUNT(*) FROM SubscriptionStateHistory WHERE subscriptionId = :subscriptionId")
    suspend fun getTransitionCount(subscriptionId: Int): Int

    @Query("""
        SELECT fromState, toState, COUNT(*) as count 
        FROM SubscriptionStateHistory 
        GROUP BY fromState, toState 
        ORDER BY count DESC
    """)
    suspend fun getTransitionStatistics(): List<TransitionStatistic>

    @Query("DELETE FROM SubscriptionStateHistory")
    suspend fun clearHistory(): Int
}
