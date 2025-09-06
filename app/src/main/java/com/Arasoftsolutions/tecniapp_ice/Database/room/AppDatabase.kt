// ==============================
// AppDatabase.kt - Clase principal de la base de datos Room
// ==============================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*

// Anotación @Database indica que esta clase define una base de datos de Room
// Se listan todas las entidades que componen las tablas y su versión de esquema
@Database(
    entities = [
        UserEntity::class,              // Usuarios
        AgenciaEntity::class,          // Agencias
        LocalizacionesEntity::class,   // Localizaciones geográficas
        MedidorEntity::class,          // Medidores eléctricos
        PueblosEntity::class,          // Pueblos/regiones
        SubregionesEntity::class,      // Subregiones del ICE
        VehiculosEntity::class,         // Vehículos asociados
        AveriaEntity::class              // AveriasNuevas
    ],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    // Métodos abstractos para acceder a cada DAO correspondiente a su entidad
    abstract fun usuarioDao(): UsuarioDao
    abstract fun agenciaDao(): AgenciaDao
    abstract fun localizacionDao(): LocalizacionDao
    abstract fun medidorDao(): MedidorDao
    abstract fun puebloDao(): PuebloDao
    abstract fun subregionDao(): SubregionDao
    abstract fun vehiculoDao(): VehiculoDao
    abstract fun averiaDao(): AveriaDao

    companion object {
        // Instancia única de la base de datos (Singleton)
        @Volatile private var INSTANCE: AppDatabase? = null

        // Método para obtener la instancia de Room
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tecniapp_room.db" // Nombre del archivo .db en almacenamiento interno
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
