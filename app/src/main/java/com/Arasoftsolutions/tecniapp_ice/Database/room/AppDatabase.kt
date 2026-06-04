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
    abstract fun luminariaReparacionDao(): LuminariaReparacionDao
    abstract fun programacionDao(): ProgramacionDao

    companion object {
        const val SCHEMA_VERSION = 30

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



        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // IF NOT EXISTS protege a instalaciones que ya tenían la tabla
                // por haber arrancado en v27 como instalación nueva.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `luminaria_reparacion` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `vehiculoId` INTEGER NOT NULL,
                        `localizacion` TEXT NOT NULL,
                        `cliente` TEXT,
                        `contacto` TEXT,
                        `observaciones` TEXT,
                        `materialesJson` TEXT,
                        `estado` TEXT NOT NULL,
                        `ejecutorNombre` TEXT NOT NULL,
                        `ejecutorCedula` TEXT,
                        `fechaRegistro` INTEGER NOT NULL,
                        `fechaCarga` INTEGER NOT NULL,
                        `fechaReparacion` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_luminaria_reparacion_vehiculoId` " +
                        "ON `luminaria_reparacion` (`vehiculoId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_luminaria_reparacion_estado` " +
                        "ON `luminaria_reparacion` (`estado`)"
                )
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Campos de planificación (supervisor)
                db.execSQL("ALTER TABLE programaciones ADD COLUMN localizacionOriginal TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN localizacionNormalizada TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN agenciaTag TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN cuadrillaAsignada TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN supervisorNombre TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN fotosSupervisorJson TEXT")

                // Campos de atención (técnico)
                db.execSQL("ALTER TABLE programaciones ADD COLUMN tecnicoPrincipalUid TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN tecnicoPrincipalNombre TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN tecnicosAtendieronJson TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN descripcionAtencion TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN fotosAtencionJson TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN gastoMateriales INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN materialesJson TEXT")

                // Fechas de atención
                db.execSQL("ALTER TABLE programaciones ADD COLUMN fechaInicioAtencion INTEGER")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN fechaFinalizacion INTEGER")

                // Reapertura
                db.execSQL("ALTER TABLE programaciones ADD COLUMN reabierta INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN fechaReapertura INTEGER")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN reabiertaPorUid TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN motivoReapertura TEXT")

                // Cancelación
                db.execSQL("ALTER TABLE programaciones ADD COLUMN canceladaPorUid TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN fechaCancelacion INTEGER")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN motivoCancelacion TEXT")

                // Eliminación lógica
                db.execSQL("ALTER TABLE programaciones ADD COLUMN eliminada INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN eliminadaPorUid TEXT")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN fechaEliminacion INTEGER")
                db.execSQL("ALTER TABLE programaciones ADD COLUMN motivoEliminacion TEXT")

                // Sincronización
                db.execSQL("ALTER TABLE programaciones ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")

                // Nuevos índices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_programaciones_eliminada` ON `programaciones` (`eliminada`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_programaciones_tecnicoPrincipalUid` ON `programaciones` (`tecnicoPrincipalUid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_programaciones_agenciaTag` ON `programaciones` (`agenciaTag`)")

                // Migrar estados anteriores al nuevo esquema
                db.execSQL("UPDATE programaciones SET estado = 'EN_ATENCION' WHERE estado = 'EN_PROCESO'")
                db.execSQL("UPDATE programaciones SET estado = 'ATENDIDA' WHERE estado = 'EJECUTADA'")

                // Inicializar tecnicoPrincipalUid desde tecnicoId en órdenes ya atendidas
                db.execSQL("""
                    UPDATE programaciones
                    SET tecnicoPrincipalUid = tecnicoId
                    WHERE estado IN ('ATENDIDA', 'EN_ATENCION')
                    AND tecnicoPrincipalUid IS NULL
                """.trimIndent())
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE averias ADD COLUMN cambioMedidorJson TEXT")
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
                    .addMigrations(MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30)
                    .fallbackToDestructiveMigrationFrom(true, 23)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
