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
package com.celzero.bravedns.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celzero.bravedns.R
import com.celzero.bravedns.database.RichHistoryEntry
import com.celzero.bravedns.database.SubscriptionStatus
import com.celzero.bravedns.databinding.ListItemPurchaseHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PurchaseHistoryAdapter(private val context: Context) :
    PagingDataAdapter<RichHistoryEntry, PurchaseHistoryAdapter.HistoryViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RichHistoryEntry>() {
            override fun areItemsTheSame(old: RichHistoryEntry, new: RichHistoryEntry) =
                old.id == new.id

            override fun areContentsTheSame(old: RichHistoryEntry, new: RichHistoryEntry) =
                old == new
        }

        private val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ListItemPurchaseHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        // getItem() may return null while a placeholder page is loading
        val entry = getItem(position) ?: return
        holder.bind(entry)
    }

    inner class HistoryViewHolder(private val b: ListItemPurchaseHistoryBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(entry: RichHistoryEntry) {
            b.tvTimestamp.text = DATE_FORMAT.format(Date(entry.timestamp))

            val productDisplay = buildString {
                append(entry.displayProductName)
                if (entry.planId.isNotBlank()) {
                    append(" · ")
                    append(entry.planId)
                }
            }
            b.tvProductName.text = productDisplay.ifBlank { context.getString(R.string.payment_history_unknown_product) }

            val stateLabel = context.getString(stateStringRes(entry.toState))
            b.tvStateBadge.text = stateLabel
            val (badgeBg, badgeFg) = stateBadgeColors(entry)
            b.tvStateBadge.backgroundTintList =
                ContextCompat.getColorStateList(context, badgeBg)
            b.tvStateBadge.setTextColor(ContextCompat.getColor(context, badgeFg))

            b.tvTransition.text = context.getString(
                R.string.payment_history_transition_fmt,
                friendlyStateName(context, entry.fromState),
                friendlyStateName(context, entry.toState)
            )

            val reason = entry.reason
            if (reason.isNullOrBlank()) {
                b.tvReason.visibility = View.GONE
            } else {
                b.tvReason.visibility = View.VISIBLE
                // Trim very long error strings
                b.tvReason.text = if (reason.length > 120) reason.take(120) + "…" else reason
            }

            val (iconRes, iconTint) = stateIconAndTint(entry)
            b.ivStatusIcon.setImageResource(iconRes)
            b.ivStatusIcon.imageTintList = ContextCompat.getColorStateList(context, iconTint)

            if (entry.purchaseToken.isNotBlank()) {
                b.tvPurchaseToken.visibility = View.VISIBLE
                val shortToken = entry.purchaseToken.take(12) + "…"
                b.tvPurchaseToken.text =
                    context.getString(R.string.payment_history_token_fmt, shortToken)
            } else {
                b.tvPurchaseToken.visibility = View.GONE
            }

            b.divider.visibility =
                if (bindingAdapterPosition == itemCount - 1) View.GONE else View.VISIBLE
        }

        private fun stateBadgeColors(entry: RichHistoryEntry): Pair<Int, Int> = when {
            entry.isSuccess -> Pair(R.color.chipBackgroundColor, R.color.colorGreen_900)
            entry.isFailure -> Pair(R.color.chipBgNegative, R.color.colorRed_A400)
            entry.isPending -> Pair(R.color.chipBgNeutral, R.color.primaryText)
            entry.toState == SubscriptionStatus.SubscriptionState.STATE_CANCELLED.id ->
                Pair(R.color.chipTextNegative, R.color.colorAmber_900)
            entry.toState == SubscriptionStatus.SubscriptionState.STATE_EXPIRED.id ->
                Pair(R.color.chipTextNeutral, R.color.primaryText)
            else -> Pair(R.color.chipTextNeutral, R.color.colorPrimary)
        }

        private fun stateIconAndTint(entry: RichHistoryEntry): Pair<Int, Int> = when {
            entry.isSuccess -> Pair(R.drawable.ic_check_circle, R.color.accentGood)
            entry.isFailure -> Pair(R.drawable.ic_stop, R.color.accentBad)
            entry.isPending -> Pair(R.drawable.ic_refresh, R.color.primaryText)
            entry.toState == SubscriptionStatus.SubscriptionState.STATE_CANCELLED.id ->
                Pair(R.drawable.ic_circle, R.color.accentBad)
            entry.toState == SubscriptionStatus.SubscriptionState.STATE_EXPIRED.id ->
                Pair(R.drawable.ic_idle_timeout, R.color.primaryText)
            else -> Pair(R.drawable.ic_info, R.color.primaryText)
        }
    }

    private fun stateStringRes(stateId: Int): Int {
        return when (SubscriptionStatus.SubscriptionState.fromId(stateId)) {
            SubscriptionStatus.SubscriptionState.STATE_ACTIVE   -> R.string.lbl_active
            SubscriptionStatus.SubscriptionState.STATE_CANCELLED -> R.string.status_cancelled
            SubscriptionStatus.SubscriptionState.STATE_EXPIRED  -> R.string.status_expired
            SubscriptionStatus.SubscriptionState.STATE_REVOKED  -> R.string.payment_history_state_revoked
            SubscriptionStatus.SubscriptionState.STATE_ACK_PENDING -> R.string.payment_history_state_pending
            SubscriptionStatus.SubscriptionState.STATE_PURCHASED -> R.string.payment_history_state_purchased
            SubscriptionStatus.SubscriptionState.STATE_PURCHASE_FAILED -> R.string.ping_status_failed
            SubscriptionStatus.SubscriptionState.STATE_GRACE    -> R.string.status_grace_period
            SubscriptionStatus.SubscriptionState.STATE_ON_HOLD  -> R.string.server_selection_sub_on_hold
            SubscriptionStatus.SubscriptionState.STATE_PAUSED   -> R.string.payment_history_state_paused
            else -> R.string.payment_history_state_unknown
        }
    }

    private fun friendlyStateName(ctx: Context, stateId: Int): String {
        return ctx.getString(stateStringRes(stateId))
    }
}
