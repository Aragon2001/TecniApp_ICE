package com.Arasoftsolutions.tecniapp_ice.Database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.Arasoftsolutions.tecniapp_ice.Database.Tablas.*
import com.google.firebase.auth.FirebaseAuth

class TecniAppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "TecniAppDB.db" // Nombre de la base de datos
        private const val DATABASE_VERSION = 1 // Versión inicial
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Crear tablas con las estructuras basadas en Firebase
        db.execSQL("CREATE TABLE Medidores (id INTEGER PRIMARY KEY, calle TEXT, cliente TEXT, localizacion TEXT, metros TEXT, poste TEXT, pueblo TEXT)")
        db.execSQL("CREATE TABLE Localizaciones (id INTEGER PRIMARY KEY, calle INTEGER, direccion TEXT, latitud DOUBLE, longitud DOUBLE, pueblo INTEGER, alPoste INTEGER, delPoste INTEGER)")
        db.execSQL("CREATE TABLE Pueblos (id INTEGER PRIMARY KEY, nombre TEXT, subregion TEXT)")
        db.execSQL("CREATE TABLE Agencias (id INTEGER PRIMARY KEY, nombre TEXT, subregion TEXT)")
        db.execSQL("CREATE TABLE Subregiones (id INTEGER PRIMARY KEY, nombre TEXT)")
        db.execSQL("CREATE TABLE Vehiculos (id INTEGER PRIMARY KEY, agencia TEXT, placa INTEGER, tipo TEXT)")
        db.execSQL("CREATE TABLE Usuarios (id INTEGER PRIMARY KEY, agencia TEXT, apellidos TEXT, cedula INTEGER, email TEXT, nombre TEXT, password TEXT, placaVehiculo INTEGER, subregion TEXT, telefono INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Eliminar tablas existentes al actualizar la versión
        db.execSQL("DROP TABLE IF EXISTS Medidores")
        db.execSQL("DROP TABLE IF EXISTS Localizaciones")
        db.execSQL("DROP TABLE IF EXISTS Pueblos")
        db.execSQL("DROP TABLE IF EXISTS Agencias")
        db.execSQL("DROP TABLE IF EXISTS Subregiones")
        db.execSQL("DROP TABLE IF EXISTS Vehiculos")
        db.execSQL("DROP TABLE IF EXISTS Usuarios")
        onCreate(db) // Crear las tablas de nuevo
    }

    // ===========================
    // Función genérica para insertar o actualizar datos
    // ===========================
    private fun <T> insertOrUpdate(tableName: String, items: List<T>, createContentValues: (T) -> ContentValues) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            items.forEach { item ->
                val contentValues = createContentValues(item)
                val id = contentValues.getAsInteger("id").toString()
                val rowsAffected = db.update(tableName, contentValues, "id = ?", arrayOf(id))
                if (rowsAffected == 0) {
                    db.insert(tableName, null, contentValues)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    // ===========================
    // Funciones de inserción/actualización específicas
    // ===========================
    fun insertOrUpdateMedidores(medidores: List<Medidores>) {
        insertOrUpdate("Medidores", medidores) { medidor ->
            ContentValues().apply {
                put("id", medidor.id)
                put("calle", medidor.calle)
                put("cliente", medidor.cliente)
                put("localizacion", medidor.localizacion)
                put("metros", medidor.metros)
                put("poste", medidor.poste)
                put("pueblo", medidor.pueblo)
            }
        }
    }

    fun insertOrUpdateLocalizaciones(localizaciones: List<Localizaciones>) {
        insertOrUpdate("Localizaciones", localizaciones) { loc ->
            ContentValues().apply {
                put("id", loc.id)
                put("calle", loc.calle)
                put("direccion", loc.direccion)
                put("latitud", loc.latitud)
                put("longitud", loc.longitud)
                put("pueblo", loc.pueblo)
                put("alPoste", loc.alPoste)
                put("delPoste", loc.delPoste)
            }
        }
    }

    fun insertOrUpdatePueblos(pueblos: List<Pueblos>) {
        insertOrUpdate("Pueblos", pueblos) { pueblo ->
            ContentValues().apply {
                put("id", pueblo.id)
                put("nombre", pueblo.nombre)
                put("subregion", pueblo.subregion)
            }
        }
    }

    fun insertOrUpdateAgencias(agencias: List<Agencias>) {
        insertOrUpdate("Agencias", agencias) { agencia ->
            ContentValues().apply {
                put("id", agencia.id)
                put("nombre", agencia.nombre)
                put("subregion", agencia.subregion)
            }
        }
    }

    fun insertOrUpdateSubregiones(subregiones: List<Subregiones>) {
        insertOrUpdate("Subregiones", subregiones) { subregion ->
            ContentValues().apply {
                put("id", subregion.id)
                put("nombre", subregion.nombre)
            }
        }
    }

    fun insertOrUpdateVehiculos(vehiculos: List<Vehiculos>) {
        insertOrUpdate("Vehiculos", vehiculos) { vehiculo ->
            ContentValues().apply {
                put("id", vehiculo.id)
                put("agencia", vehiculo.agencia)
                put("placa", vehiculo.placa)
                put("tipo", vehiculo.tipo)
            }
        }
    }

    // Inserta o actualiza un único usuario
    fun insertOrUpdateUser(usuario: User) {
        insertOrUpdate("Usuarios", listOf(usuario)) { user ->
            ContentValues().apply {
                put("id", user.id)
                put("agencia", user.agencia)
                put("apellidos", user.apellidos)
                put("cedula", user.cedula)
                put("email", user.email)
                put("nombre", user.nombre)
                put("placaVehiculo", user.placaVehiculo)
                put("subregion", user.subregion)
                put("telefono", user.telefono)
            }
        }
    }



    // ===========================
    // Función genérica para obtener datos
    // ===========================
    private fun <T> getAllFromTable(tableName: String, createObject: (cursor: android.database.Cursor) -> T): List<T> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $tableName", null)
        val items = mutableListOf<T>()
        if (cursor.moveToFirst()) {
            do {
                items.add(createObject(cursor))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return items
    }


    // ===========================
// Obtener todos los registros de "Medidores"
// ===========================
fun getMedidores(): List<Medidores> = getAllFromTable("Medidores") { cursor ->
    // Mapear cada fila de la tabla "Medidores" a un objeto de la clase "Medidores"
    Medidores(
        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")), // ID del medidor
        cliente = cursor.getString(cursor.getColumnIndexOrThrow("cliente")), // Nombre del cliente
        localizacion = cursor.getString(cursor.getColumnIndexOrThrow("localizacion")), // Ubicación del medidor
        metros = cursor.getString(cursor.getColumnIndexOrThrow("metros")), // Metros asociados al medidor
        poste = cursor.getString(cursor.getColumnIndexOrThrow("poste")), // Poste relacionado
        pueblo = cursor.getString(cursor.getColumnIndexOrThrow("pueblo")), // Pueblo relacionado
         calle = cursor.getString(cursor.getColumnIndexOrThrow("calle")) // Pueblo relacionado
    )
}
// ===========================
// Obtener todos los registros de "Agencias"
// ===========================
fun getAgencias(): List<Agencias> = getAllFromTable("Agencias") { cursor ->
    // Mapear cada fila de la tabla "Agencias" a un objeto de la clase "Agencias"
    Agencias(
        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")), // ID de la agencia
        nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre")), // Nombre de la agencia
        subregion = cursor.getString(cursor.getColumnIndexOrThrow("subregion")) // Subregión asociada
    )
}

// ===========================
// Obtener todos los registros de "Localizaciones"
// ===========================
fun getLocalizaciones(): List<Localizaciones> = getAllFromTable("Localizaciones") { cursor ->
    // Mapear cada fila de la tabla "Localizaciones" a un objeto de la clase "Localizaciones"
    Localizaciones(
        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")), // ID de la localización
        calle = cursor.getInt(cursor.getColumnIndexOrThrow("calle")), // Número de calle
        direccion = cursor.getString(cursor.getColumnIndexOrThrow("direccion")), // Dirección completa
        latitud = cursor.getDouble(cursor.getColumnIndexOrThrow("latitud")), // Coordenada de latitud
        longitud = cursor.getDouble(cursor.getColumnIndexOrThrow("longitud")), // Coordenada de longitud
        pueblo = cursor.getInt(cursor.getColumnIndexOrThrow("pueblo")), // Pueblo asociado
        alPoste = cursor.getInt(cursor.getColumnIndexOrThrow("alPoste")), // Distancia al poste
        delPoste = cursor.getInt(cursor.getColumnIndexOrThrow("delPoste")) // Distancia desde el poste
    )
}



// ===========================
// Obtener todos los registros de "Pueblos"
// ===========================
fun getPueblos(): List<Pueblos> = getAllFromTable("Pueblos") { cursor ->
    // Mapear cada fila de la tabla "Pueblos" a un objeto de la clase "Pueblos"
    Pueblos(
        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")), // ID del pueblo
        nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre")), // Nombre del pueblo
        subregion = cursor.getString(cursor.getColumnIndexOrThrow("subregion")) // Subregión asociada
    )
}

// ===========================
// Obtener todos los registros de "Subregiones"
// ===========================
fun getSubregiones(): List<Subregiones> = getAllFromTable("Subregiones") { cursor ->
    // Mapear cada fila de la tabla "Subregiones" a un objeto de la clase "Subregiones"
    Subregiones(
        id = cursor.getInt(cursor.getColumnIndexOrThrow("id")), // ID de la subregión
        nombre = cursor.getString(cursor.getColumnIndexOrThrow("nombre")) // Nombre de la subregión
    )
}

    // Obtener el registro del usuario autenticado
    fun getUser(): User? {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM Usuarios WHERE id = ?", arrayOf(currentUserUid))
        cursor.use {
            return if (it.moveToFirst()) {
                User(
                    id = it.getInt(it.getColumnIndexOrThrow("id")),
                    agencia = it.getString(it.getColumnIndexOrThrow("agencia")),
                    apellidos = it.getString(it.getColumnIndexOrThrow("apellidos")),
                    cedula = it.getString(it.getColumnIndexOrThrow("cedula")),
                    email = it.getString(it.getColumnIndexOrThrow("email")),
                    nombre = it.getString(it.getColumnIndexOrThrow("nombre")),
                    placaVehiculo = it.getString(it.getColumnIndexOrThrow("placaVehiculo")),
                    subregion = it.getString(it.getColumnIndexOrThrow("subregion")),
                    telefono = it.getString(it.getColumnIndexOrThrow("telefono"))
                )
            } else null
        }
    }




    // ===========================
// Obtener todos los registros de "Vehiculos"
// ===========================
fun getVehiculos(): List<Vehiculos> = getAllFromTable("Vehiculos") { cursor ->
    // Mapear cada fila de la tabla "Vehiculos" a un objeto de la clase "Vehiculos"
    Vehiculos(
    id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
        agencia = cursor.getString(cursor.getColumnIndexOrThrow("agencia")),
        placa = cursor.getString(cursor.getColumnIndexOrThrow("placa")),
        tipo = cursor.getString(cursor.getColumnIndexOrThrow("tipo"))
    )
}

}
