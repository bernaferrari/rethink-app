package com.celzero.bravedns.database

import android.content.Context
import com.celzero.bravedns.R

fun RethinkDnsEndpoint.isEditable(context: Context): Boolean {
    return this.name == context.getString(R.string.rdns_plus)
}
