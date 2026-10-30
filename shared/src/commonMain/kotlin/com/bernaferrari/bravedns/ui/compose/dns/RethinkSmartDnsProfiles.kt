package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Modifier
import com.bernaferrari.bravedns.ui.compose.theme.SharedDimensions
import com.bernaferrari.bravedns.ui.compose.theme.SectionHeader
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.bernaferrari.bravedns.data.SmartDnsProfile
import com.bernaferrari.bravedns.ui.compose.theme.RethinkListGroup
import com.bernaferrari.bravedns.ui.compose.theme.RethinkRadioListItem
import com.bernaferrari.bravedns.ui.compose.theme.cardPositionFor

@Composable
fun RethinkSmartDnsProfiles(profile: SmartDnsProfile, labels: SmartDnsProfileLabels,
    statusText: String? = null,
    onSelect: (SmartDnsProfile) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SharedDimensions.spacingSm)) {
        SectionHeader(labels.title)
        if (statusText != null) Text(statusText, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        RethinkListGroup {
            SmartDnsProfile.entries.forEachIndexed { index, choice ->
                val (title, description) = labels.choices.getValue(choice)
                RethinkRadioListItem(headline = title, supporting = description,
                    selected = profile == choice, onSelect = { onSelect(choice) },
                    position = cardPositionFor(index, SmartDnsProfile.entries.size))
            }
        }
    }
}
