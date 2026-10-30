package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.bernaferrari.bravedns.ui.compose.theme.CardPosition
import com.bernaferrari.bravedns.ui.compose.theme.RethinkCheckboxListItem
import com.bernaferrari.bravedns.ui.compose.theme.RethinkFormTextField

/** DoH keeps its authenticated URL separate from optional numeric bootstrap destinations. */
@Composable
fun RethinkDohEndpointEditor(
    title: String,
    nameLabel: String,
    endpointLabel: String,
    defaultName: String,
    initialEndpoint: String,
    insecureLabel: String,
    strings: RethinkEndpointEditorStrings,
    bootstrapLabel: String,
    bootstrapDescription: String,
    onSubmit: (name: String, endpoint: String, isSecure: Boolean, bootstrap: String) -> String?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(defaultName) { mutableStateOf(defaultName) }
    var endpoint by rememberSaveable(initialEndpoint) { mutableStateOf(initialEndpoint) }
    var bootstrap by rememberSaveable { mutableStateOf("") }
    var insecure by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    RethinkEndpointEditorForm(title, error, strings, onDismiss,
        onConfirm = { error = onSubmit(name, endpoint, !insecure, bootstrap).orEmpty() }) {
        RethinkFormTextField(name, onValueChange = { name = it }, label = nameLabel)
        RethinkFormTextField(endpoint, onValueChange = { endpoint = it }, label = endpointLabel,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        RethinkFormTextField(bootstrap, onValueChange = { bootstrap = it }, label = bootstrapLabel,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
        Text(bootstrapDescription, style = MaterialTheme.typography.bodySmall)
        RethinkCheckboxListItem(headline = insecureLabel, checked = insecure,
            onCheckedChange = { insecure = it }, position = CardPosition.Single)
    }
}
