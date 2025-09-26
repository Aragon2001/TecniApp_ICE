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
        AgenciaEntity::class,           // Agencias
        LocalizacionesEntity::class,    // Localizaciones geográficas
        MedidorEntity::class,           // Medidores eléctricos
        PueblosEntity::class,           // Pueblos/regiones
        SubregionesEntity::class,       // Subregiones del ICE
        VehiculosEntity::class,         // Vehículos asociados
        AveriaEntity::class             // Averías
    ],
    version = 4,                        // ⬅️ súbelo si cambias el schema
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun usuarioDao(): UsuarioDao
    abstract fun agenciaDao(): AgenciaDao
    abstract fun localizacionDao(): LocalizacionDao
    abstract fun medidorDao(): MedidorDao
    abstract fun puebloDao(): PuebloDao
    abstract fun subregionDao(): SubregionDao
    abstract fun vehiculoDao(): VehiculoDao
    abstract fun averiaDao(): AveriaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tecniapp_room.db"
                )
                    .fallbackToDestructiveMigration(true) // ✅ aquí va, antes de build()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
