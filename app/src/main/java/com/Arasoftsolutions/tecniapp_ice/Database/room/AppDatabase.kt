package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*

@Database(
    entities = [
        UserEntity::class,
        RegionEntity::class,
        AgenciaEntity::class,
        LocalizacionesEntity::class,
        MedidorEntity::class,
        PueblosEntity::class,
        SubregionesEntity::class,
        VehiculoEntity::class,
        VehiculoLogEntity::class,
        MaterialEntity::class,
        TecnicoEntity::class,
        AveriaEntity::class,
        InventarioItemEntity::class,
        LuminariaReparacionEntity::class,
        InventarioMovimientoAveriaEntity::class,
    ],
    version = AppDatabase.SCHEMA_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun agenciaDao(): AgenciaDao
    abstract fun regionDao(): RegionDao
    abstract fun localizacionDao(): LocalizacionDao
    abstract fun medidorDao(): MedidorDao
    abstract fun puebloDao(): PuebloDao
    abstract fun subregionDao(): SubregionDao
    abstract fun vehiculoDao(): VehiculoDao
    abstract fun vehiculoLogDao(): VehiculoLogDao
    abstract fun materialDao(): MaterialDao
    abstract fun tecnicoDao(): TecnicoDao
    abstract fun averiaDao(): AveriaDao
    abstract fun inventarioDao(): InventarioDao

    companion object {
        const val SCHEMA_VERSION = 25

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehiculo_log` (
                        `logId` TEXT NOT NULL,
                        `vehiculoId` TEXT NOT NULL,
                        `tipo` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `km` REAL,
                        `payloadJson` TEXT NOT NULL,
                        `syncState` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`logId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehiculo_log_vehiculoId_timestamp` ON `vehiculo_log` (`vehiculoId`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehiculo_log_vehiculoId_syncState` ON `vehiculo_log` (`vehiculoId`, `syncState`)")

                runCatching { db.execSQL("ALTER TABLE vehiculos ADD COLUMN kmActual REAL NOT NULL DEFAULT 0.0") }
                runCatching { db.execSQL("UPDATE vehiculos SET kmActual = COALESCE(kilometrajeActual, 0.0)") }
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tecniapp_room.db"
                )
                    .addMigrations(MIGRATION_24_25)
                    .fallbackToDestructiveMigrationFrom(true, 23)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
