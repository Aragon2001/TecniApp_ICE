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
        ProgramacionEntity::class,
        ProgramacionFotoEntity::class,
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
    abstract fun programacionDao(): ProgramacionDao

    companion object {
        const val SCHEMA_VERSION = 27

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



        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `programaciones` (
                        `programacionId` TEXT NOT NULL,
                        `vehiculoId` TEXT NOT NULL,
                        `placa` TEXT NOT NULL,
                        `localizacion` TEXT NOT NULL,
                        `circuito` TEXT NOT NULL,
                        `cuenta` TEXT NOT NULL,
                        `actividad` TEXT NOT NULL,
                        `descripcion` TEXT,
                        `lat` REAL,
                        `lng` REAL,
                        `estado` TEXT NOT NULL,
                        `observaciones` TEXT,
                        `fechaAsignacion` INTEGER NOT NULL,
                        `fechaEjecucion` INTEGER,
                        `supervisorId` TEXT NOT NULL,
                        `tecnicoId` TEXT NOT NULL,
                        `subregion` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`programacionId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_programaciones_vehiculoId` ON `programaciones` (`vehiculoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_programaciones_tecnicoId` ON `programaciones` (`tecnicoId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_programaciones_subregion` ON `programaciones` (`subregion`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_programaciones_estado` ON `programaciones` (`estado`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_programaciones_updatedAt` ON `programaciones` (`updatedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `programacion_fotos` (
                        `fotoId` TEXT NOT NULL,
                        `programacionId` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `tipo` TEXT NOT NULL,
                        PRIMARY KEY(`fotoId`),
                        FOREIGN KEY(`programacionId`) REFERENCES `programaciones`(`programacionId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_programacion_fotos_programacionId` ON `programacion_fotos` (`programacionId`)")
            }
        }



        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE averias ADD COLUMN evidenciasJson TEXT")
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
                     .addMigrations(MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)
                    .fallbackToDestructiveMigrationFrom(true, 23)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
