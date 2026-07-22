/*
 * Copyright 2026 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.celzero.bravedns.ui.compose.rpn

import Logger
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.celzero.bravedns.R
import com.celzero.bravedns.database.SubscriptionStateHistoryDao
import com.celzero.bravedns.database.SubscriptionStatusDao
import com.celzero.bravedns.rpnproxy.RpnProxyManager
import com.celzero.bravedns.scheduler.BugReportZipper
import com.celzero.bravedns.scheduler.EnhancedBugReport
import com.celzero.bravedns.ui.compose.theme.RethinkBottomSheetActionRow
import com.celzero.bravedns.ui.compose.theme.RethinkBottomSheetCard
import com.celzero.bravedns.ui.compose.theme.RethinkFilterChip
import com.celzero.bravedns.ui.compose.theme.RethinkModalBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Compose equivalent of the old support activity: user report plus selected diagnostics. */
@Composable
internal fun RpnSupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var includeStatus by remember { mutableStateOf(true) }
    var includeHistory by remember { mutableStateOf(true) }
    var includeStats by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }

    RethinkModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        includeBottomSpacer = false,
        verticalSpacing = 12.dp,
    ) {
        RethinkBottomSheetCard(
            modifier = Modifier.heightIn(max = 560.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.rpn_support_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.rpn_support_description),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        stringResource(R.string.rpn_support_category_payment),
                        stringResource(R.string.rpn_support_category_activation),
                        stringResource(R.string.rpn_support_category_connectivity),
                        stringResource(R.string.rpn_support_category_refund),
                        stringResource(R.string.rpn_support_category_other),
                    ).forEach { item ->
                        RethinkFilterChip(
                            selected = category == item,
                            onClick = { category = if (category == item) null else item },
                            label = item,
                        )
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.rpn_support_category_label)) },
                    minLines = 3,
                    enabled = !sending,
                )
                HorizontalDivider()
                SupportToggle(stringResource(R.string.rpn_support_include_subscription), includeStatus, !sending) { includeStatus = it }
                SupportToggle(stringResource(R.string.rpn_support_include_history), includeHistory, !sending) { includeHistory = it }
                SupportToggle(stringResource(R.string.rpn_support_include_diagnostics), includeStats, !sending) { includeStats = it }
            }
        }
        RethinkBottomSheetActionRow(
            primaryText = stringResource(if (sending) R.string.rpn_support_preparing else R.string.rpn_support_create_email),
            primaryEnabled = !sending && (description.isNotBlank() || category != null),
            onPrimaryClick = {
                sending = true
                scope.launch {
                    val attachment = withContext(Dispatchers.IO) {
                        RpnSupportDiagnostics.create(
                            context = context,
                            description = description.trim(),
                            category = category,
                            includeStatus = includeStatus,
                            includeHistory = includeHistory,
                            includeStats = includeStats,
                        )
                    }
                    sending = false
                    RpnSupportDiagnostics.launchEmail(context, description.trim(), category, attachment)
                    onDismiss()
                }
            },
            secondaryText = stringResource(R.string.lbl_cancel),
            onSecondaryClick = onDismiss,
            secondaryEnabled = !sending,
        )
    }
}

@Composable
private fun SupportToggle(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

private object RpnSupportDiagnostics : KoinComponent {
    private val statusDao by inject<SubscriptionStatusDao>()
    private val historyDao by inject<SubscriptionStateHistoryDao>()

    suspend fun create(
        context: Context,
        description: String,
        category: String?,
        includeStatus: Boolean,
        includeHistory: Boolean,
        includeStats: Boolean,
    ): File? = runCatching {
        val statuses = if (includeStatus) statusDao.getAllSubscriptions() else emptyList()
        val history = if (includeHistory) historyDao.getRecentHistory(50) else emptyList()
        val entitlement = if (includeStats) RpnProxyManager.getEntitlementDetails() else null
        val report = buildString {
            appendLine("RETHINK PLUS SUPPORT DIAGNOSTIC REPORT")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date())}")
            appendLine("App version: ${appVersion(context)}")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("USER REPORT")
            category?.let { appendLine("Category: $it") }
            appendLine(description.ifBlank { "(no description provided)" })
            if (includeStatus) {
                appendLine("\nSUBSCRIPTION STATUS (${statuses.size})")
                statuses.forEach { status ->
                    appendLine("state=${status.state}, product=${status.productId}, account=${redact(status.accountId)}, token=${redact(status.purchaseToken)}")
                    appendLine("purchase=${status.purchaseTime}, billingExpiry=${status.billingExpiry}, updated=${status.lastUpdatedTs}")
                }
            }
            if (includeHistory) {
                appendLine("\nSTATE HISTORY (${history.size})")
                history.forEach { entry -> appendLine("${entry.timestamp}: ${entry.fromStateName} -> ${entry.toStateName}${entry.reason?.let { ": $it" }.orEmpty()}") }
            }
            if (includeStats) {
                appendLine("\nENTITLEMENT")
                if (entitlement == null) appendLine("unavailable") else {
                    appendLine("status=${entitlement.status()}, cid=${redact(entitlement.cid())}, did=${redact(entitlement.did(), 4)}")
                    appendLine("expiry=${entitlement.expiry()}, provider=${entitlement.providerID()}, test=${entitlement.test()}, allowRestore=${entitlement.allowRestore()}")
                }
            }
        }
        val supportDir = File(context.filesDir, "support").apply { mkdirs() }
        val reportFile = File(supportDir, "rpn_support_diagnostic.txt").apply { writeText(report) }
        val archive = File(supportDir, "rpn_support_diagnostics.zip")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            addFile(zip, reportFile, "diagnostic_report.txt")
            readWirelogTail(context)?.let { bytes ->
                zip.putNextEntry(ZipEntry("wirelogs.txt")); zip.write(bytes); zip.closeEntry()
            }
            EnhancedBugReport.getTombstoneZipFile(context)?.let { addFile(zip, it, "bugreport.zip") }
        }
        archive.takeIf { it.exists() && it.length() > 0L }
    }.getOrNull()

    fun launchEmail(context: Context, description: String, category: String?, attachment: File?) {
        val recipient = context.getString(R.string.about_mail_to)
        val body = buildString {
            appendLine("Hello Rethink Support,")
            appendLine()
            category?.let { appendLine("Issue category: $it\n") }
            appendLine(description.ifBlank { "Please see the attached diagnostic report." })
            appendLine("\nThe selected subscription status, state history, and device diagnostics are attached.")
        }
        val attachmentUri = attachment?.let {
            runCatching { FileProvider.getUriForFile(context, BugReportZipper.FILE_PROVIDER_NAME, it) }.getOrNull()
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.rpn_support_subject) + category?.let { ": $it" }.orEmpty())
            putExtra(Intent.EXTRA_TEXT, body)
            attachmentUri?.let { uri ->
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(context.contentResolver, "Support diagnostics", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.rpn_support_email_chooser))) }
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        if (!file.exists() || file.length() == 0L) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun readWirelogTail(context: Context): ByteArray? {
        val file = File(context.filesDir, "${Logger.WIRELOG_FOLDER_NAME}/${Logger.WIRELOG_FILE_NAME}")
        if (!file.exists() || file.length() == 0L) return null
        val length = minOf(file.length(), 1L * 1024L * 1024L).toInt()
        return RandomAccessFile(file, "r").use { input ->
            input.seek(file.length() - length)
            ByteArray(length).also(input::readFully)
        }
    }

    private fun redact(value: String?, visible: Int = 12): String = when {
        value.isNullOrBlank() -> "N/A"
        value.length <= visible -> value
        else -> "${value.take(visible)}***"
    }

    private fun appVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
}
