/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.celzero.bravedns.database

import android.content.Context
import com.celzero.bravedns.R

fun DnsProxyEndpoint.getExplanationText(context: Context, appName: String): String {
    return if (this.isSelected) {
        if (this.proxyAppName != context.getString(R.string.cd_custom_dns_proxy_default_app)) {
            context.getString(
                R.string.settings_socks_forwarding_desc,
                this.proxyIP,
                this.proxyPort.toString(),
                appName
            )
        } else {
            context.getString(
                R.string.settings_socks_forwarding_desc_no_app,
                this.proxyIP,
                this.proxyPort.toString()
            )
        }
    } else {
        if (this.proxyAppName != context.getString(R.string.cd_custom_dns_proxy_default_app)) {
            context.getString(
                R.string.dns_proxy_desc,
                this.proxyIP,
                this.proxyPort.toString(),
                appName
            )
        } else {
            context.getString(
                R.string.dns_proxy_desc_no_app,
                this.proxyIP,
                this.proxyPort.toString()
            )
        }
    }
}

fun DnsProxyEndpoint.isInternal(context: Context): Boolean {
    return this.proxyType == context.getString(R.string.cd_dns_proxy_mode_internal)
}
