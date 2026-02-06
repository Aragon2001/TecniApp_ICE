package com.Arasoftsolutions.tecniapp_ice.pm.model.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pm_sync_errors",
    indices = [
        Index(value = ["entityId", "type"]),
        Index(value = ["createdAt"])
    ]
)
data class SyncErrorLogEntity(
    @PrimaryKey
    val errorId: String,
    val type: String,
    val entityId: String,
    val message: String,
    val stacktrace: String?,
    val createdAt: Long
)
