package com.bernaferrari.bravedns.ui.compose.database

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

internal fun Context.copyToClipboard(label: String, text: String, copiedMessage: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, copiedMessage, Toast.LENGTH_SHORT).show()
}
