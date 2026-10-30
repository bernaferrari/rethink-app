package com.bernaferrari.bravedns.ui.compose.dns

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import com.bernaferrari.bravedns.data.SmartDnsApplyStatus
import com.bernaferrari.bravedns.data.forSelection
import com.bernaferrari.bravedns.service.PersistentState
import org.koin.compose.koinInject
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.bernaferrari.bravedns.service.VpnController
import androidx.compose.ui.res.stringResource
import com.bernaferrari.bravedns.R
import com.bernaferrari.bravedns.data.SmartDnsProfile

@Composable
internal fun SmartDnsProfileSetting() {
    val preferences = koinInject<PersistentState>()
    val scope = rememberCoroutineScope()
    val profile by produceState(SmartDnsProfile.fromValue(preferences.smartDnsProfile), preferences) {
        preferences.preferenceKeyChanges().collect { key ->
            if (key == PersistentState.SMART_DNS_PROFILE) value = SmartDnsProfile.fromValue(preferences.smartDnsProfile)
        }
    }
    val status by preferences.smartDnsApplyStatus.collectAsState()
    val statusText = when (status.forSelection(profile)) {
        SmartDnsApplyStatus.Saved -> stringResource(R.string.smart_dns_profile_saved)
        is SmartDnsApplyStatus.Queued -> stringResource(R.string.smart_dns_profile_queued)
        is SmartDnsApplyStatus.Settling -> stringResource(R.string.smart_dns_profile_settling)
        is SmartDnsApplyStatus.Active -> stringResource(R.string.smart_dns_profile_active)
        is SmartDnsApplyStatus.Failed -> stringResource(R.string.smart_dns_profile_failed)
    }
    val labels = SmartDnsProfileLabels(stringResource(R.string.smart_dns_profile_heading), mapOf(
        SmartDnsProfile.None to (stringResource(R.string.smart_dns_profile_none) to stringResource(R.string.smart_dns_profile_none_desc)),
        SmartDnsProfile.Security to (stringResource(R.string.smart_dns_profile_security) to stringResource(R.string.smart_dns_profile_security_desc)),
        SmartDnsProfile.Privacy to (stringResource(R.string.smart_dns_profile_privacy) to stringResource(R.string.smart_dns_profile_privacy_desc)),
        SmartDnsProfile.Family to (stringResource(R.string.smart_dns_profile_family) to stringResource(R.string.smart_dns_profile_family_desc)),
    ))
    RethinkSmartDnsProfiles(profile, labels, statusText) { selected ->
        preferences.queueSmartDnsProfile(selected)
        preferences.smartDnsProfile = selected.value
        // An explicit call also retries the same value after native failure; preferences may not re-emit it.
        scope.launch(Dispatchers.IO) { VpnController.applySmartDnsProfile() }
    }
}
