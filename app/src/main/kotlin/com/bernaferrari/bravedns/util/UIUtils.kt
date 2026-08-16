/*
 * Copyright 2023 RethinkDNS and its authors
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
package com.bernaferrari.bravedns.util

import Logger
import Logger.LOG_TAG_UI
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.Html
import android.text.Spanned
import android.text.format.DateUtils
import android.widget.Toast

import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.database.DnsLog
import com.bernaferrari.bravedns.image.FavIconDownloader
import com.bernaferrari.bravedns.net.doh.Transaction
import com.bernaferrari.bravedns.service.DnsLogTracker
import com.celzero.firestack.backend.Backend
import com.celzero.firestack.backend.NetStat
import java.util.Locale
import java.util.Calendar
import java.util.Date
import java.util.regex.Matcher
import java.util.regex.Pattern

object UIUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0

        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }

        return if (value == value.toLong().toDouble()) {
            "${value.toLong()} ${units[unitIndex]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[unitIndex])
        }
    }


    fun getDnsStatusStringRes(status: Int?): Int {
        if (status == null) return R.string.failed_using_default

        return when (Transaction.Status.fromId(status)) {
            Transaction.Status.START -> {
                R.string.lbl_starting
            }
            Transaction.Status.COMPLETE -> {
                R.string.dns_connected
            }
            Transaction.Status.SEND_FAIL -> {
                R.string.status_no_internet
            }
            Transaction.Status.TRANSPORT_ERROR -> {
                R.string.status_dns_server_down
            }
            Transaction.Status.NO_RESPONSE -> {
                R.string.status_dns_server_down
            }
            Transaction.Status.BAD_RESPONSE -> {
                R.string.status_dns_error
            }
            Transaction.Status.BAD_QUERY -> {
                R.string.status_dns_error
            }
            Transaction.Status.CLIENT_ERROR -> {
                R.string.status_dns_error
            }
            Transaction.Status.INTERNAL_ERROR -> {
                R.string.status_failing
            }
        }
    }

    fun getProxyStatusStringRes(statusId: Int?): Int {
        return when (statusId) {
            Backend.TUP -> {
                R.string.lbl_starting
            }
            Backend.TOK -> {
                R.string.dns_connected
            }
            Backend.TZZ -> {
                R.string.lbl_idle
            }
            Backend.TKO -> {
                R.string.status_failing
            }
            Backend.END -> {
                R.string.lbl_stopped
            }
            Backend.TNT -> {
                R.string.status_waiting
            }
            Backend.TPU -> {
                R.string.pause_text
            }
            else -> {
                R.string.rt_filter_parent_selected
            }
        }
    }

    enum class ProxyStatus(val id: Int) {
        TOK(Backend.TOK),
        TUP(Backend.TUP),
        TZZ(Backend.TZZ),
        TPU(Backend.TPU), // paused
        TNT(Backend.TNT),
        TKO(Backend.TKO),
        END(Backend.END)
    }

    fun formatToRelativeTime(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        return if (DateUtils.isToday(timestamp)) {
            context.getString(R.string.relative_time_today)
        } else if (isYesterday(Date(timestamp))) {
            context.getString(R.string.relative_time_yesterday)
        } else {
            val d =
                DateUtils.getRelativeTimeSpanString(
                    timestamp,
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
            d.toString()
        }
    }

    // ref: https://stackoverflow.com/a/3006423
    private fun isYesterday(day: Date): Boolean {
        val c1 = Calendar.getInstance()
        c1.add(Calendar.DAY_OF_YEAR, -1)
        val c2 = Calendar.getInstance()
        c2.time = day
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    fun openVpnProfile(context: Context) {
        try {
            val intent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Intent(Settings.ACTION_VPN_SETTINGS)
                } else {
                    Intent(Constants.ACTION_VPN_SETTINGS_INTENT)
                }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.vpn_profile_error),
                Toast.LENGTH_SHORT
            )
            Logger.w(Logger.LOG_TAG_VPN, "Failure opening app info: ${e.message}", e)
        }
    }

    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            context.startActivity(intent)
        } catch (e: Exception) {
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.intent_launch_error, url),
                Toast.LENGTH_SHORT
            )
            Logger.w(LOG_TAG_UI, "activity not found ${e.message}", e)
        }
    }

    fun openNetworkSettings(context: Context, settings: String): Boolean {
        return try {
            val intent = Intent(settings)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            val msg = context.getString(R.string.intent_launch_error, settings)
            Utilities.showToastUiCentered(context, msg, Toast.LENGTH_SHORT)
            Logger.w(Logger.LOG_TAG_VPN, "err opening android setting: ${e.message}", e)
            false
        }
    }

    fun openAppInfo(context: Context) {
        val packageName = context.packageName
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.app_info_error),
                Toast.LENGTH_SHORT
            )
            Logger.w(LOG_TAG_UI, "activity not found ${e.message}", e)
        }
    }

    fun clipboardCopy(context: Context, s: String, label: String) {
        val clipboard: ClipboardManager? = context.getSystemService()
        val clip = ClipData.newPlainText(label, s)
        clipboard?.setPrimaryClip(clip)
    }

    fun htmlToSpannedText(text: String): Spanned {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
        } else {
            HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    fun sendEmailIntent(context: Context) {
        val intent =
            Intent(Intent.ACTION_SENDTO).apply {
                data = context.getString(R.string.about_mail_to_string).toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.about_mail_to)))
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.about_mail_subject))
            }
        context.startActivity(
            Intent.createChooser(
                intent,
                context.getString(R.string.about_mail_bugreport_share_title)
            )
        )
    }

    fun openAndroidAppInfo(context: Context, packageName: String?) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) { // ActivityNotFoundException | NullPointerException
            Logger.w(Logger.LOG_TAG_FIREWALL, "Failure calling app info: ${e.message}", e)
            Utilities.showToastUiCentered(
                context,
                context.getString(R.string.ctbs_app_info_not_available_toast),
                Toast.LENGTH_SHORT
            )
        }
    }

    suspend fun fetchFavIcon(context: Context, dnsLog: DnsLog) {
        if (dnsLog.groundedQuery()) return

        if (isDgaDomain(dnsLog.queryStr)) return

        Logger.d(LOG_TAG_UI, "Coil - fetchFavIcon():${dnsLog.queryStr}")

        // fetch fav icon in background using Coil
        FavIconDownloader(context, dnsLog.queryStr).run()
    }

    // check if the domain is generated by a DGA (Domain Generation Algorithm)
    private fun isDgaDomain(fqdn: String): Boolean {
        // dnsleaktest.com fqdn's are auto-generated
        if (fqdn.contains(DnsLogTracker.DNS_LEAK_TEST)) return true

        // fqdn's which has uuids are auto-generated
        return containsUuid(fqdn)
    }

    private fun containsUuid(fqdn: String): Boolean {
        // ref: https://stackoverflow.com/a/39611414
        val regex = "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}"
        val pattern: Pattern = Pattern.compile(regex)
        val matcher: Matcher = pattern.matcher(fqdn)
        return matcher.find()
    }

    fun getCountryNameFromFlag(flag: String?): String {
        if (flag == null) return ""

        val flagCodePoints =
            mapOf(
                "🇦🇨" to "Ascension Island",
                "🇦🇩" to "Andorra",
                "🇦🇪" to "United Arab Emirates",
                "🇦🇫" to "Afghanistan",
                "🇦🇬" to "Antigua & Barbuda",
                "🇦🇮" to "Anguilla",
                "🇦🇱" to "Albania",
                "🇦🇲" to "Armenia",
                "🇦🇴" to "Angola",
                "🇦🇶" to "Antarctica",
                "🇦🇷" to "Argentina",
                "🇦🇸" to "American Samoa",
                "🇦🇹" to "Austria",
                "🇦🇺" to "Australia",
                "🇦🇼" to "Aruba",
                "🇦🇽" to "Åland Islands",
                "🇦🇿" to "Azerbaijan",
                "🇧🇦" to "Bosnia & Herzegovina",
                "🇧🇧" to "Barbados",
                "🇧🇩" to "Bangladesh",
                "🇧🇪" to "Belgium",
                "🇧🇫" to "Burkina Faso",
                "🇧🇬" to "Bulgaria",
                "🇧🇭" to "Bahrain",
                "🇧🇮" to "Burundi",
                "🇧🇯" to "Benin",
                "🇧🇱" to "St. Barthélemy",
                "🇧🇲" to "Bermuda",
                "🇧🇳" to "Brunei",
                "🇧🇴" to "Bolivia",
                "🇧🇶" to "Caribbean Netherlands",
                "🇧🇷" to "Brazil",
                "🇧🇸" to "Bahamas",
                "🇧🇹" to "Bhutan",
                "🇧🇻" to "Bouvet Island",
                "🇧🇼" to "Botswana",
                "🇧🇾" to "Belarus",
                "🇧🇿" to "Belize",
                "🇨🇦" to "Canada",
                "🇨🇨" to "Cocos (Keeling) Islands",
                "🇨🇩" to "Congo - Kinshasa",
                "🇨🇫" to "Central African Republic",
                "🇨🇬" to "Congo - Brazzaville",
                "🇨🇭" to "Switzerland",
                "🇨🇮" to "Côte d’Ivoire",
                "🇨🇰" to "Cook Islands",
                "🇨🇱" to "Chile",
                "🇨🇲" to "Cameroon",
                "🇨🇳" to "China",
                "🇨🇴" to "Colombia",
                "🇨🇵" to "Clipperton Island",
                "🇨🇷" to "Costa Rica",
                "🇨🇺" to "Cuba",
                "🇨🇻" to "Cape Verde",
                "🇨🇼" to "Curaçao",
                "🇨🇽" to "Christmas Island",
                "🇨🇾" to "Cyprus",
                "🇨🇿" to "Czechia",
                "🇩🇪" to "Germany",
                "🇩🇬" to "Diego Garcia",
                "🇩🇯" to "Djibouti",
                "🇩🇰" to "Denmark",
                "🇩🇲" to "Dominica",
                "🇩🇴" to "Dominican Republic",
                "🇩🇿" to "Algeria",
                "🇪🇦" to "Ceuta & Melilla",
                "🇪🇨" to "Ecuador",
                "🇪🇪" to "Estonia",
                "🇪🇬" to "Egypt",
                "🇪🇭" to "Western Sahara",
                "🇪🇷" to "Eritrea",
                "🇪🇸" to "Spain",
                "🇪🇹" to "Ethiopia",
                "🇪🇺" to "European Union",
                "🇫🇮" to "Finland",
                "🇫🇯" to "Fiji",
                "🇫🇰" to "Falkland Islands",
                "🇫🇲" to "Micronesia",
                "🇫🇴" to "Faroe Islands",
                "🇫🇷" to "France",
                "🇬🇦" to "Gabon",
                "🇬🇧" to "United Kingdom",
                "🇬🇩" to "Grenada",
                "🇬🇪" to "Georgia",
                "🇬🇫" to "French Guiana",
                "🇬🇬" to "Guernsey",
                "🇬🇭" to "Ghana",
                "🇬🇮" to "Gibraltar",
                "🇬🇱" to "Greenland",
                "🇬🇲" to "Gambia",
                "🇬🇳" to "Guinea",
                "🇬🇵" to "Guadeloupe",
                "🇬🇶" to "Equatorial Guinea",
                "🇬🇷" to "Greece",
                "🇬🇸" to "South Georgia & South Sandwich Islands",
                "🇬🇹" to "Guatemala",
                "🇬🇺" to "Guam",
                "🇬🇼" to "Guinea-Bissau",
                "🇬🇾" to "Guyana",
                "🇭🇰" to "Hong Kong SAR China",
                "🇭🇲" to "Heard & McDonald Islands",
                "🇭🇳" to "Honduras",
                "🇭🇷" to "Croatia",
                "🇭🇹" to "Haiti",
                "🇭🇺" to "Hungary",
                "🇮🇨" to "Canary Islands",
                "🇮🇩" to "Indonesia",
                "🇮🇪" to "Ireland",
                "🇮🇱" to "Israel",
                "🇮🇲" to "Isle of Man",
                "🇮🇳" to "India",
                "🇮🇴" to "British Indian Ocean Territory",
                "🇮🇶" to "Iraq",
                "🇮🇷" to "Iran",
                "🇮🇸" to "Iceland",
                "🇮🇹" to "Italy",
                "🇯🇪" to "Jersey",
                "🇯🇲" to "Jamaica",
                "🇯🇴" to "Jordan",
                "🇯🇵" to "Japan",
                "🇰🇪" to "Kenya",
                "🇰🇬" to "Kyrgyzstan",
                "🇰🇭" to "Cambodia",
                "🇰🇮" to "Kiribati",
                "🇰🇲" to "Comoros",
                "🇰🇳" to "St. Kitts & Nevis",
                "🇰🇵" to "North Korea",
                "🇰🇷" to "South Korea",
                "🇰🇼" to "Kuwait",
                "🇰🇾" to "Cayman Islands",
                "🇰🇿" to "Kazakhstan",
                "🇱🇦" to "Laos",
                "🇱🇧" to "Lebanon",
                "🇱🇨" to "St. Lucia",
                "🇱🇮" to "Liechtenstein",
                "🇱🇰" to "Sri Lanka",
                "🇱🇷" to "Liberia",
                "🇱🇸" to "Lesotho",
                "🇱🇹" to "Lithuania",
                "🇱🇺" to "Luxembourg",
                "🇱🇻" to "Latvia",
                "🇱🇾" to "Libya",
                "🇲🇦" to "Morocco",
                "🇲🇨" to "Monaco",
                "🇲🇩" to "Moldova",
                "🇲🇪" to "Montenegro",
                "🇲🇫" to "St. Martin",
                "🇲🇬" to "Madagascar",
                "🇲🇭" to "Marshall Islands",
                "🇲🇰" to "North Macedonia",
                "🇲🇱" to "Mali",
                "🇲🇲" to "Myanmar (Burma)",
                "🇲🇳" to "Mongolia",
                "🇲🇴" to "Macao SAR China",
                "🇲🇵" to "Northern Mariana Islands",
                "🇲🇶" to "Martinique",
                "🇲🇷" to "Mauritania",
                "🇲🇸" to "Montserrat",
                "🇲🇹" to "Malta",
                "🇲🇺" to "Mauritius",
                "🇲🇻" to "Maldives",
                "🇲🇼" to "Malawi",
                "🇲🇽" to "Mexico",
                "🇲🇾" to "Malaysia",
                "🇲🇿" to "Mozambique",
                "🇳🇦" to "Namibia",
                "🇳🇨" to "New Caledonia",
                "🇳🇪" to "Niger",
                "🇳🇫" to "Norfolk Island",
                "🇳🇬" to "Nigeria",
                "🇳🇮" to "Nicaragua",
                "🇳🇱" to "Netherlands",
                "🇳🇴" to "Norway",
                "🇳🇵" to "Nepal",
                "🇳🇷" to "Nauru",
                "🇳🇺" to "Niue",
                "🇳🇿" to "New Zealand",
                "🇴🇲" to "Oman",
                "🇵🇦" to "Panama",
                "🇵🇪" to "Peru",
                "🇵🇫" to "French Polynesia",
                "🇵🇬" to "Papua New Guinea",
                "🇵🇭" to "Philippines",
                "🇵🇰" to "Pakistan",
                "🇵🇱" to "Poland",
                "🇵🇲" to "St. Pierre & Miquelon",
                "🇵🇳" to "Pitcairn Islands",
                "🇵🇷" to "Puerto Rico",
                "🇵🇸" to "Palestinian Territories",
                "🇵🇹" to "Portugal",
                "🇵🇼" to "Palau",
                "🇵🇾" to "Paraguay",
                "🇶🇦" to "Qatar",
                "🇷🇪" to "Réunion",
                "🇷🇴" to "Romania",
                "🇷🇸" to "Serbia",
                "🇷🇺" to "Russia",
                "🇷🇼" to "Rwanda",
                "🇸🇦" to "Saudi Arabia",
                "🇸🇧" to "Solomon Islands",
                "🇸🇨" to "Seychelles",
                "🇸🇩" to "Sudan",
                "🇸🇪" to "Sweden",
                "🇸🇬" to "Singapore",
                "🇸🇭" to "St. Helena",
                "🇸🇮" to "Slovenia",
                "🇸🇯" to "Svalbard & Jan Mayen",
                "🇸🇰" to "Slovakia",
                "🇸🇱" to "Sierra Leone",
                "🇸🇲" to "San Marino",
                "🇸🇳" to "Senegal",
                "🇸🇴" to "Somalia",
                "🇸🇷" to "Suriname",
                "🇸🇸" to "South Sudan",
                "🇸🇹" to "São Tomé & Príncipe",
                "🇸🇻" to "El Salvador",
                "🇸🇽" to "Sint Maarten",
                "🇸🇾" to "Syria",
                "🇸🇿" to "Eswatini",
                "🇹🇦" to "Tristan da Cunha",
                "🇹🇨" to "Turks & Caicos Islands",
                "🇹🇩" to "Chad",
                "🇹🇫" to "French Southern Territories",
                "🇹🇬" to "Togo",
                "🇹🇭" to "Thailand",
                "🇹🇯" to "Tajikistan",
                "🇹🇰" to "Tokelau",
                "🇹🇱" to "Timor-Leste",
                "🇹🇲" to "Turkmenistan",
                "🇹🇳" to "Tunisia",
                "🇹🇴" to "Tonga",
                "🇹🇷" to "Turkey",
                "🇹🇹" to "Trinidad & Tobago",
                "🇹🇻" to "Tuvalu",
                "🇹🇼" to "Taiwan",
                "🇹🇿" to "Tanzania",
                "🇺🇦" to "Ukraine",
                "🇺🇬" to "Uganda",
                "🇺🇲" to "U.S. Outlying Islands",
                "🇺🇳" to "United Nations",
                "🇺🇸" to "United States",
                "🇺🇾" to "Uruguay",
                "🇺🇿" to "Uzbekistan",
                "🇻🇦" to "Vatican City",
                "🇻🇨" to "St. Vincent & Grenadines",
                "🇻🇪" to "Venezuela",
                "🇻🇬" to "British Virgin Islands",
                "🇻🇮" to "U.S. Virgin Islands",
                "🇻🇳" to "Vietnam",
                "🇻🇺" to "Vanuatu",
                "🇼🇫" to "Wallis & Futuna",
                "🇼🇸" to "Samoa",
                "🇽🇰" to "Kosovo",
                "🇾🇪" to "Yemen",
                "🇾🇹" to "Mayotte",
                "🇿🇦" to "South Africa",
                "🇿🇲" to "Zambia",
                "🇿🇼" to "Zimbabwe"
            )
        return flagCodePoints[flag] ?: "--"
    }

    fun getAccentColor(appTheme: Int): Int {
        return when (appTheme) {
            Themes.LIGHT.id, Themes.LIGHT_PLUS.id -> R.color.accentGoodLight
            Themes.DARK.id -> R.color.accentGood
            Themes.DARK_PLUS.id, Themes.SYSTEM_DEFAULT.id -> R.color.accentGoodBlack
            else -> R.color.accentGoodBlack
        }
    }

    // get time in seconds and add "sec" or "min" or "hr" or "day" accordingly
    fun getDurationInHumanReadableFormat(context: Context, inputSeconds: Int): String {
        // calculate the time in seconds and return the value in seconds or minutes or hours or days
        val secondsInMinute = 60
        val secondsInHour = 3600
        val secondsInDay = 86400

        val days = inputSeconds / secondsInDay
        val remainingSecondsAfterDays = inputSeconds % secondsInDay
        val hours = remainingSecondsAfterDays / secondsInHour
        val remainingSecondsAfterHours = remainingSecondsAfterDays % secondsInHour
        val minutes = remainingSecondsAfterHours / secondsInMinute
        val seconds = remainingSecondsAfterHours % secondsInMinute

        val result = StringBuilder()

        if (days > 0) {
            result.append("$days ${context.getString(R.string.lbl_day)} ")
        }
        if (hours > 0) {
            result.append("$hours ${context.getString(R.string.lbl_hour)} ")
        }
        if (minutes > 0) {
            result.append("$minutes ${context.getString(R.string.lbl_min)} ")
        }
        if (seconds > 0 || (days == 0 && hours == 0 && minutes == 0)) {
            result.append("$seconds ${context.getString(R.string.lbl_sec)} ")
        }

        return result.toString().trim()
    }

    fun formatNetStat(stat: NetStat?): String? {
        if (stat == null) return null

        val ip = stat.ip()?.toString()
        val udp = stat.udp()?.toString()
        val tcp = stat.tcp()?.toString()
        val fwd = stat.fwd()?.toString()
        val icmp = stat.icmp()?.toString()
        val nic = stat.nic()?.toString()
        val rdnsInfo = stat.rdnsinfo()?.toString()
        val nicInfo = stat.nicinfo()?.toString()
        val tun = stat.tun()?.toString()

        var stats = nic + nicInfo + tun + fwd + ip + icmp + tcp + udp + rdnsInfo
        stats = stats.replace("{", "\n")
        stats = stats.replace("}", "\n\n")
        stats = stats.replace(",", "\n")

        return stats
    }


}
