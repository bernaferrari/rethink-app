/*
 * Copyright 2026 RethinkDNS and its authors
 */
package com.bernaferrari.bravedns.database

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/**
 * Intermediate schemas 28/29 were never exported in this fork (only 27.json and 30.json exist).
 * Diff of those files is additive: CountryConfig + indices, SubscriptionStatus columns, and
 * WgConfigFiles.isLockdown. Tables are rebuilt (not ALTER … DEFAULT) so sqlite_master matches
 * Room's expected CREATE TABLE exactly.
 */
internal val MIGRATION_27_30: Migration =
    object : Migration(27, 30) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `CountryConfig` (`id` TEXT NOT NULL, `cc` TEXT NOT NULL, `name` TEXT NOT NULL, `address` TEXT NOT NULL, `city` TEXT NOT NULL, `key` TEXT NOT NULL, `load` INTEGER NOT NULL, `link` INTEGER NOT NULL, `count` INTEGER NOT NULL, `premium` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `catchAll` INTEGER NOT NULL, `lockdown` INTEGER NOT NULL, `mobileOnly` INTEGER NOT NULL, `ssidBased` INTEGER NOT NULL, `priority` INTEGER NOT NULL, `ssids` TEXT NOT NULL, `lastModified` INTEGER NOT NULL, `selectionCount` INTEGER NOT NULL, `isFavourite` INTEGER NOT NULL, `hopEnabled` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_CountryConfig_cc` ON `CountryConfig` (`cc`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_CountryConfig_isActive` ON `CountryConfig` (`isActive`)"
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_CountryConfig_isFavourite` ON `CountryConfig` (`isFavourite`)"
            )

            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `SubscriptionStatus_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` TEXT NOT NULL, `deviceId` TEXT NOT NULL, `purchaseToken` TEXT NOT NULL, `orderId` TEXT NOT NULL, `productId` TEXT NOT NULL, `planId` TEXT NOT NULL, `sessionToken` TEXT NOT NULL, `productTitle` TEXT NOT NULL, `state` INTEGER NOT NULL, `purchaseTime` INTEGER NOT NULL, `accountExpiry` INTEGER NOT NULL, `billingExpiry` INTEGER NOT NULL, `developerPayload` TEXT NOT NULL, `status` INTEGER NOT NULL, `windowDays` INTEGER NOT NULL, `lastUpdatedTs` INTEGER NOT NULL, `previousProductId` TEXT NOT NULL, `previousPurchaseToken` TEXT NOT NULL, `replacedAt` INTEGER NOT NULL)"
            )
            connection.execSQL(
                "INSERT INTO `SubscriptionStatus_new` (`id`, `accountId`, `deviceId`, `purchaseToken`, `orderId`, `productId`, `planId`, `sessionToken`, `productTitle`, `state`, `purchaseTime`, `accountExpiry`, `billingExpiry`, `developerPayload`, `status`, `windowDays`, `lastUpdatedTs`, `previousProductId`, `previousPurchaseToken`, `replacedAt`) SELECT `id`, `accountId`, '', `purchaseToken`, '', `productId`, `planId`, `sessionToken`, `productTitle`, `state`, `purchaseTime`, `accountExpiry`, `billingExpiry`, `developerPayload`, `status`, 3, `lastUpdatedTs`, '', '', 0 FROM `SubscriptionStatus`"
            )
            connection.execSQL("DROP TABLE `SubscriptionStatus`")
            connection.execSQL("ALTER TABLE `SubscriptionStatus_new` RENAME TO `SubscriptionStatus`")

            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `WgConfigFiles_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `configPath` TEXT NOT NULL, `serverResponse` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isCatchAll` INTEGER NOT NULL, `isLockdown` INTEGER NOT NULL, `oneWireGuard` INTEGER NOT NULL, `useOnlyOnMetered` INTEGER NOT NULL, `isDeletable` INTEGER NOT NULL, `ssidEnabled` INTEGER NOT NULL, `ssids` TEXT NOT NULL, `modifiedTs` INTEGER NOT NULL)"
            )
            connection.execSQL(
                "INSERT INTO `WgConfigFiles_new` (`id`, `name`, `configPath`, `serverResponse`, `isActive`, `isCatchAll`, `isLockdown`, `oneWireGuard`, `useOnlyOnMetered`, `isDeletable`, `ssidEnabled`, `ssids`, `modifiedTs`) SELECT `id`, `name`, `configPath`, `serverResponse`, `isActive`, `isCatchAll`, 0, `oneWireGuard`, `useOnlyOnMetered`, `isDeletable`, `ssidEnabled`, `ssids`, `modifiedTs` FROM `WgConfigFiles`"
            )
            connection.execSQL("DROP TABLE `WgConfigFiles`")
            connection.execSQL("ALTER TABLE `WgConfigFiles_new` RENAME TO `WgConfigFiles`")
        }
    }
