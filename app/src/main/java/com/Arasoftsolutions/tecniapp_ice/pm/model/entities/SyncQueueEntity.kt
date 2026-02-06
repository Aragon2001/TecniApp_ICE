package com.Arasoftsolutions.tecniapp_ice.pm.model.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncQueueType
import com.Arasoftsolutions.tecniapp_ice.pm.model.SyncStatus

@Entity(
    tableName = "pm_sync_queue",
    indices = [
        Index(value = ["type", "entityId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["availableAt"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    val queueId: String,
    val type: SyncQueueType,
    val entityId: String,
    val status: SyncStatus,
    val retryCount: Int,
    val lastError: String?,
    val createdAt: Long,
    val availableAt: Long
)
