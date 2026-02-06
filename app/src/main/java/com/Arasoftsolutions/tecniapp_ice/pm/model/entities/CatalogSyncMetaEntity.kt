package com.Arasoftsolutions.tecniapp_ice.pm.model.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_sync_meta")
data class CatalogSyncMetaEntity(
    @PrimaryKey
    val catalogId: String,
    val remoteVersion: Long,
    val lastSyncAt: Long
)
