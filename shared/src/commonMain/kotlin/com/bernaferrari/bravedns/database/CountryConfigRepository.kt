/*
 * Copyright 2025 RethinkDNS and its authors
 * Licensed under the Apache License, Version 2.0
 */
package com.bernaferrari.bravedns.database

import androidx.room3.Transaction

class CountryConfigRepository(private val dao: CountryConfigDAO) {
    suspend fun getAllConfigs(): List<CountryConfig> = dao.getAllConfigs()
    @Transaction suspend fun insert(config: CountryConfig) = dao.insert(config)
    @Transaction suspend fun insertAll(configs: List<CountryConfig>) = dao.insertAll(configs)
    @Transaction suspend fun update(config: CountryConfig) = dao.update(config)
    @Transaction suspend fun delete(config: CountryConfig) = dao.delete(config)
    suspend fun updateSsidBased(key: String, value: Boolean) = dao.updateSsidBased(key, value)
    suspend fun getCount(): Int = dao.getCount()
    suspend fun getById(id: String): CountryConfig? = dao.getById(id)

    suspend fun syncServers(newServers: List<CountryConfig>): Int {
        val existing = dao.getAllConfigs()
        val existingIds = existing.mapTo(mutableSetOf()) { it.id }
        val newIds = newServers.mapTo(mutableSetOf()) { it.id }
        existing.filter { it.id !in newIds && it.id != AUTO_SERVER_ID }.forEach { dao.delete(it) }
        newServers.filter { it.id !in existingIds }.let { if (it.isNotEmpty()) dao.insertAll(it) }
        newServers.filter { it.id in existingIds }.forEach {
            dao.updateServer(it.id, it.name, it.address, it.city, it.key, it.load, it.link, it.count, it.isActive)
        }
        return newServers.size
    }

    suspend fun updateSsids(key: String, ssids: String) = dao.updateSsids(key, ssids)
    suspend fun incrementSelectionCount(key: String) {
        if (key.isNotBlank() && !key.equals(AUTO_SERVER_ID, ignoreCase = true)) dao.incrementSelectionCount(key)
    }
    suspend fun updateFavourite(cc: String, isFavourite: Boolean) = dao.updateFavouriteByCountryCode(cc, isFavourite)
    suspend fun getTopFrequentCcs(limit: Int = 5): List<String> = dao.getTopFrequentCcs(limit)
    suspend fun resetUserSelections() {
        dao.resetAllIsEnabled()
        dao.resetAllIsFavourite()
        dao.resetAllSelectionCounts()
    }

    private companion object {
        const val AUTO_SERVER_ID = "AUTO"
    }
}
