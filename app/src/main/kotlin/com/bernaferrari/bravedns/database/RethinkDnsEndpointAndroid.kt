package com.bernaferrari.bravedns.database

import android.content.Context
import com.bernaferrari.bravedns.R

fun RethinkDnsEndpoint.isEditable(context: Context): Boolean {
    return this.name == context.getString(R.string.rdns_plus)
}
