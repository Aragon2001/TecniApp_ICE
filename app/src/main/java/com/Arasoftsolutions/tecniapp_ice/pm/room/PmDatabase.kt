package com.Arasoftsolutions.tecniapp_ice.pm.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.Arasoftsolutions.tecniapp_ice.pm.model.RoomConverters
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.ActivityGroupEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.CatalogSyncMetaEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.OrdenSapEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.PlanillaEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.SyncErrorLogEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.SyncQueueEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.WorkLogEntity
import com.Arasoftsolutions.tecniapp_ice.pm.room.dao.ActivityGroupDao
import com.Arasoftsolutions.tecniapp_ice.pm.room.dao.CatalogSyncMetaDao
import com.Arasoftsolutions.tecniapp_ice.pm.room.dao.OrdenSapDao
import com.Arasoftsolutions.tecniapp_ice.pm.room.dao.PlanillaDao
import com.Arasoftsolutions.tecniapp_ice.pm.room.dao.SyncErrorLogDao
import com.Arasoftsolutions.tecniapp_ice.pm.room.dao.SyncQueueDao
import com.Arasoftsolutions.tecniapp_ice.pm.room.dao.WorkLogDao

@Database(
    entities = [
        OrdenSapEntity::class,
        CatalogSyncMetaEntity::class,
        ActivityGroupEntity::class,
        WorkLogEntity::class,
        PlanillaEntity::class,
        SyncQueueEntity::class,
        SyncErrorLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class PmDatabase : RoomDatabase() {
    abstract fun ordenSapDao(): OrdenSapDao
    abstract fun catalogSyncMetaDao(): CatalogSyncMetaDao
    abstract fun activityGroupDao(): ActivityGroupDao
    abstract fun workLogDao(): WorkLogDao
    abstract fun planillaDao(): PlanillaDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncErrorLogDao(): SyncErrorLogDao
}
