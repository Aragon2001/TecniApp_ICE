package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        VehiculosEntity::class,
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
        const val SCHEMA_VERSION = 24

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tecniapp_room.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
