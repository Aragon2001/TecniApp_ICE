// app/src/main/java/com/Arasoftsolutions/tecniapp_ice/Database/room/AppDatabase.kt
package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*

@Database(
    entities = [
        UserEntity::class,              // Usuarios
        RegionEntity::class,            // Regiones del ICE
        AgenciaEntity::class,           // Agencias
        LocalizacionesEntity::class,    // Localizaciones geográficas
        MedidorEntity::class,           // Medidores eléctricos
        PueblosEntity::class,           // Pueblos/regiones
        SubregionesEntity::class,       // Subregiones del ICE
        VehiculosEntity::class,         // Vehículos asociados
        MaterialEntity::class,          // Catálogo de materiales
        TecnicoEntity::class,           // Personal técnico
        AveriaEntity::class,            // Averías
        InventarioItemEntity::class,    // Inventario por vehículo
        LuminariaReparacionEntity::class, // Reparaciones de luminarias
        InventarioMovimientoAveriaEntity::class, // Movimientos de inventario por avería
        RegistroDiarioEntity::class,    // Registros diarios (km/orímetro)
        RegistroMantenimientoEntity::class // Mantenimientos
    ],

    version = AppDatabase.SCHEMA_VERSION,                        // ✅ mantener la versión más alta

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
    abstract fun registroVehiculoDao(): RegistroVehiculoDao
    abstract fun materialDao(): MaterialDao
    abstract fun tecnicoDao(): TecnicoDao
    abstract fun averiaDao(): AveriaDao
    abstract fun inventarioDao(): InventarioDao

    companion object {
        const val SCHEMA_VERSION = 23

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tecniapp_room.db"
                )
                    .fallbackToDestructiveMigration(true) // ⚠️ elimina datos si cambia el schema
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
