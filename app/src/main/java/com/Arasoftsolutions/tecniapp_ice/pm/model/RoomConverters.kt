package com.Arasoftsolutions.tecniapp_ice.pm.model

import androidx.room.TypeConverter

class RoomConverters {
    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? {
        return value?.let { SyncStatus.valueOf(it) }
    }

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toSyncQueueType(value: String?): SyncQueueType? {
        return value?.let { SyncQueueType.valueOf(it) }
    }

    @TypeConverter
    fun fromSyncQueueType(type: SyncQueueType?): String? {
        return type?.name
    }
}
