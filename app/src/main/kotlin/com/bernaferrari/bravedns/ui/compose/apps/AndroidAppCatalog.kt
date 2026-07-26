package com.bernaferrari.bravedns.ui.compose.apps

import com.bernaferrari.bravedns.service.FirewallManager

/** Android adapter over Rethink's live, tracked installed-app catalog. */
object AndroidAppCatalog : AppCatalog {
    override suspend fun appsForUid(uid: Int): List<AppCatalogEntry> {
        val packageNames = FirewallManager.getPackageNamesByUid(uid)
        val appNames = FirewallManager.getAppNamesByUid(uid)
        return packageNames.mapIndexed { index, packageName ->
            AppCatalogEntry(
                uid = uid,
                packageName = packageName,
                appName = appNames.getOrNull(index).orEmpty().ifBlank { packageName },
            )
        }
    }
}
