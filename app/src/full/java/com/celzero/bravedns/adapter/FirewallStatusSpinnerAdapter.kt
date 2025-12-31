/*
 * Copyright 2022 RethinkDNS and its authors
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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.celzero.bravedns.R
import com.celzero.bravedns.util.UIUtils

class FirewallStatusSpinnerAdapter(val context: Context, private val spinnerLabels: Array<String>) :
    BaseAdapter() {

    private data class Holder(
        val root: LinearLayout,
        val textView: AppCompatTextView,
        val iconView: AppCompatImageView
    )

    override fun getCount(): Int {
        return spinnerLabels.size
    }

    override fun getItem(position: Int): String {
        return spinnerLabels[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder = (convertView?.tag as? Holder) ?: createHolder(parent)
        bind(holder, getItem(position))
        return holder.root
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder = (convertView?.tag as? Holder) ?: createHolder(parent)
        bind(holder, getItem(position))
        return holder.root
    }

    private fun createHolder(parent: ViewGroup): Holder {
        val padding = dpToPx(5f)
        val layout =
            LinearLayout(parent.context).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(15f), padding, padding, padding)
                gravity = Gravity.CENTER_VERTICAL
            }
        val tv =
            AppCompatTextView(parent.context).apply {
                setTextColor(UIUtils.fetchColor(context, R.attr.primaryTextColor))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(dpToPx(10f), 0, dpToPx(10f), 0)
            }
        val iv =
            AppCompatImageView(parent.context).apply {
                setImageResource(R.drawable.ic_arrow_down)
                visibility = View.INVISIBLE
            }
        layout.addView(tv)
        layout.addView(iv)
        val holder = Holder(layout, tv, iv)
        layout.tag = holder
        return holder
    }

    private fun bind(holder: Holder, status: String?) {
        holder.textView.text = status.orEmpty()
        holder.iconView.visibility = View.INVISIBLE
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
