package com.Arasoftsolutions.tecniapp_ice.Database.sync

import com.Arasoftsolutions.tecniapp_ice.Database.Tablas.*
import com.Arasoftsolutions.tecniapp_ice.Database.TecniAppDatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * Clase que gestiona la sincronización entre Firebase y la base de datos local.
 */
class FirebaseSyncManager(private val TecniAppDatabase: TecniAppDatabaseHelper) {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    // Referencias a las rutas de Firebase
    private val medidoresRef = database.getReference("Medidores/SubRegion Guapiles")
    private val localizacionesRef = database.getReference("Localizaciones")
    private val pueblosRef = database.getReference("Pueblos")
    private val subregionesRef = database.getReference("Subregiones")
    private val agenciasRef = database.getReference("Agencias")
    private val vehiculosRef = database.getReference("Vehiculos")
    private val usuariosRef = database.getReference("Usuarios")

    // Ejecuta la sincronización de todas las entidades
    fun sincronizarFirebaseConLocal() {
        sincronizarAgencias()
        sincronizarLocalizaciones()
        sincronizarMedidores()
        sincronizarPueblos()
        sincronizarSubregiones()
        sincronizarVehiculos()
        sincronizarUsuarioAutenticado()
    }

    // Sincroniza datos de agencias
    private fun sincronizarAgencias() {
        agenciasRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val agenciasList = mutableListOf<Agencias>()
                for (agenciaSnapshot in snapshot.children) {
                    val id = agenciaSnapshot.child("id").getValue(Int::class.java) ?: continue
                    val nombre = agenciaSnapshot.child("nombre").getValue(String::class.java) ?: ""
                    val subregion = agenciaSnapshot.child("subregion").getValue(String::class.java) ?: ""
                    // Agrega agencia a la lista local
                    agenciasList.add(Agencias(id, nombre, subregion))
                }
                // Inserta o actualiza agencias en la base local
                TecniAppDatabase.insertOrUpdateAgencias(agenciasList)
            }
            override fun onCancelled(error: DatabaseError) {
                println("Error al sincronizar agencias: ${error.message}")
            }
        })

        // Sube las agencias locales a Firebase
        val agenciasLocales = TecniAppDatabase.getAgencias()
        agenciasLocales.forEach { agencia ->
            val agenciaMap = mapOf(
                "id" to agencia.id,
                "nombre" to agencia.nombre,
                "subregion" to agencia.subregion
            )
            agenciasRef.child(agencia.id.toString()).setValue(agenciaMap)
        }
    }

    // Sincroniza datos de localizaciones
    private fun sincronizarLocalizaciones() {
        localizacionesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val localizacionesList = mutableListOf<Localizaciones>()
                for (localizacionSnapshot in snapshot.children) {
                    val id = localizacionSnapshot.key?.toIntOrNull() ?: continue
                    val calle = localizacionSnapshot.child("calle").getValue(Int::class.java) ?: 0
                    val direccion = localizacionSnapshot.child("direccion").getValue(String::class.java) ?: ""
                    val latitud = localizacionSnapshot.child("latitud").getValue(Double::class.java) ?: 0.0
                    val longitud = localizacionSnapshot.child("longitud").getValue(Double::class.java) ?: 0.0
                    val pueblo = localizacionSnapshot.child("pueblo").getValue(Int::class.java) ?: 0
                    val alPoste = localizacionSnapshot.child("al_poste").getValue(Int::class.java) ?: 0
                    val delPoste = localizacionSnapshot.child("del_poste").getValue(Int::class.java) ?: 0

                    // Agrega localizacion a la lista local
                    localizacionesList.add(
                        Localizaciones(id, calle, direccion, latitud, longitud, pueblo, alPoste, delPoste)
                    )
                }
                // Inserta o actualiza localizaciones en base local
                TecniAppDatabase.insertOrUpdateLocalizaciones(localizacionesList)
            }
            override fun onCancelled(error: DatabaseError) {
                println("Error al sincronizar localizaciones: ${error.message}")
            }
        })

        // Sube las localizaciones locales a Firebase
        val localizacionesLocales = TecniAppDatabase.getLocalizaciones()
        localizacionesLocales.forEach { localizacion ->
            val localizacionMap = mapOf(
                "calle" to localizacion.calle,
                "direccion" to localizacion.direccion,
                "latitud" to localizacion.latitud,
                "longitud" to localizacion.longitud,
                "pueblo" to localizacion.pueblo,
                "al_poste" to localizacion.alPoste,
                "del_poste" to localizacion.delPoste
            )
            localizacionesRef.child(localizacion.id.toString()).setValue(localizacionMap)
        }
    }

    // Sincroniza datos de medidores
    private fun sincronizarMedidores() {
        medidoresRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val medidoresList = mutableListOf<Medidores>()
                for (medidorSnapshot in snapshot.children) {
                    val medidorNumber = medidorSnapshot.key ?: continue // Usa la clave como medidorNumber
                    val cliente = medidorSnapshot.child("cliente").getValue(String::class.java) ?: ""
                    val localizacion = medidorSnapshot.child("localizacion").getValue(String::class.java) ?: ""
                    val metros = medidorSnapshot.child("metros").getValue(String::class.java) ?: ""
                    val poste = medidorSnapshot.child("poste").getValue(String::class.java) ?: ""
                    val pueblo = medidorSnapshot.child("pueblo").getValue(String::class.java) ?: ""
                    val calle = medidorSnapshot.child("calle").getValue(String::class.java) ?: ""

                    // Agrega medidor a la lista local
                    medidoresList.add(Medidores(medidorNumber, calle, cliente, localizacion, metros, poste, pueblo))
                }
                // Inserta o actualiza medidores en base local
                TecniAppDatabase.insertOrUpdateMedidores(medidoresList)
            }
            override fun onCancelled(error: DatabaseError) {
                println("Error al sincronizar medidores: ${error.message}")
            }
        })

        // Sube los medidores locales a Firebase
        val medidoresLocales = TecniAppDatabase.getMedidores()
        medidoresLocales.forEach { medidor ->
            val medidorMap = mapOf(
                "cliente" to medidor.cliente,
                "localizacion" to medidor.localizacion,
                "metros" to medidor.metros,
                "poste" to medidor.poste,
                "pueblo" to medidor.pueblo,
                "calle" to medidor.calle
            )
            medidoresRef.child(medidor.medidorNumber).setValue(medidorMap)
        }
    }

    // ---------------------- ENTIDAD: PUEBLOS ----------------------

/**
 * Sincroniza la entidad "Pueblos" entre Firebase y la base de datos local.
 */
private fun sincronizarPueblos() {
    // Sincronización desde Firebase hacia la base de datos local
    pueblosRef.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val pueblosList = mutableListOf<Pueblos>()
            for (puebloSnapshot in snapshot.children) {
                val id = puebloSnapshot.key?.toIntOrNull() ?: continue
                val nombre = puebloSnapshot.child("nombre").getValue(String::class.java) ?: ""
                val subregion = puebloSnapshot.child("subregion").getValue(String::class.java) ?: ""

                pueblosList.add(Pueblos(id, nombre, subregion))
            }
            TecniAppDatabase.insertOrUpdatePueblos(pueblosList)
        }

        override fun onCancelled(error: DatabaseError) {
            println("Error al sincronizar pueblos: ${error.message}")
        }
    })

    // Sincronización desde la base de datos local hacia Firebase
    val pueblosLocales = TecniAppDatabase.getPueblos()
    pueblosLocales.forEach { pueblo ->
        val puebloMap = mapOf(
            "nombre" to pueblo.nombre,
            "subregion" to pueblo.subregion
        )
        pueblosRef.child(pueblo.id.toString()).setValue(puebloMap)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    println("Pueblo sincronizado con éxito en Firebase.")
                } else {
                    println("Error al sincronizar pueblo en Firebase: ${task.exception?.message}")
                }
            }
    }
}

// ---------------------- ENTIDAD: SUBREGIONES ----------------------

/**
 * Sincroniza la entidad "Subregiones" entre Firebase y la base de datos local.
 */
private fun sincronizarSubregiones() {
    subregionesRef.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val subregionesList = mutableListOf<Subregiones>()
            for (subregionSnapshot in snapshot.children) {
                val id = subregionSnapshot.key?.toIntOrNull() ?: continue
                val nombre = subregionSnapshot.child("nombre").getValue(String::class.java) ?: ""


                subregionesList.add(Subregiones(id, nombre))
            }
            TecniAppDatabase.insertOrUpdateSubregiones(subregionesList)
        }

        override fun onCancelled(error: DatabaseError) {
            println("Error al sincronizar subregiones: ${error.message}")
        }
    })

    val subregionesLocales = TecniAppDatabase.getSubregiones()
    subregionesLocales.forEach { subregion ->
        val subregionMap = mapOf(
            "nombre" to subregion.nombre,

        )
        subregionesRef.child(subregion.id.toString()).setValue(subregionMap)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    println("Subregión sincronizada con éxito en Firebase.")
                } else {
                    println("Error al sincronizar subregión en Firebase: ${task.exception?.message}")
                }
            }
    }
}

// ---------------------- ENTIDAD: VEHÍCULOS ----------------------

/**
 * Sincroniza la entidad "Vehículos" entre Firebase y la base de datos local.
 */
private fun sincronizarVehiculos() {
    vehiculosRef.addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val vehiculosList = mutableListOf<Vehiculos>()
            for (vehiculoSnapshot in snapshot.children) {
                val id = vehiculoSnapshot.key?.toIntOrNull() ?: continue
                val tipo = vehiculoSnapshot.child("tipo").getValue(String::class.java) ?: ""
                val placa = vehiculoSnapshot.child("placaVehiculo").getValue(String::class.java) ?: ""
                val agencia = vehiculoSnapshot.child("agencia").getValue(String::class.java) ?: ""

                vehiculosList.add(Vehiculos(id, tipo, placa.toString(), agencia))
            }
            TecniAppDatabase.insertOrUpdateVehiculos(vehiculosList)
        }

        override fun onCancelled(error: DatabaseError) {
            println("Error al sincronizar vehículos: ${error.message}")
        }
    })

    val vehiculosLocales = TecniAppDatabase.getVehiculos()
    vehiculosLocales.forEach { vehiculo ->
        val vehiculoMap = mapOf(
            "tipo" to vehiculo.tipo,
            "placaVehiculo" to vehiculo.placa,
            "agencia" to vehiculo.agencia
        )
        vehiculosRef.child(vehiculo.id.toString()).setValue(vehiculoMap)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    println("Vehículo sincronizado con éxito en Firebase.")
                } else {
                    println("Error al sincronizar vehículo en Firebase: ${task.exception?.message}")
                }
            }
    }
}

// ---------------------- ENTIDAD: USUARIOS ----------------------

    private fun sincronizarUsuarioAutenticado() {
        // Usamos el correo del usuario autenticado para localizar su registro
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

        if (currentUserEmail != null) {
            // Consulta Firebase buscando por el correo electrónico
            val query = usuariosRef.orderByChild("email").equalTo(currentUserEmail)
            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Si el usuario existe en Firebase
                    if (snapshot.exists()) {
                        val userSnap = snapshot.children.first()
                        val id = userSnap.key?.toIntOrNull() ?: return
                        val agencia = userSnap.child("agencia").getValue(String::class.java) ?: ""
                        val apellidos = userSnap.child("apellidos").getValue(String::class.java) ?: ""
                        val cedula = userSnap.child("cedula").getValue(String::class.java) ?: ""
                        val email = userSnap.child("email").getValue(String::class.java) ?: ""
                        val nombre = userSnap.child("nombre").getValue(String::class.java) ?: ""
                        val placaVehiculo = userSnap.child("placaVehiculo").getValue(String::class.java) ?: ""
                        val subregion = userSnap.child("subregion").getValue(String::class.java) ?: ""
                        val telefono = userSnap.child("telefono").getValue(String::class.java) ?: ""

                        // Crear objeto usuario con los datos obtenidos
                        val usuario = User(id, agencia, apellidos, cedula, email, nombre, placaVehiculo, subregion, telefono)

                        // Insertar o actualizar el usuario en la base de datos local
                        TecniAppDatabase.insertOrUpdateUser(usuario)
                        println("Usuario sincronizado desde Firebase a base de datos local.")
                    } else {
                        println("No se encontró el usuario en Firebase.")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    println("Error al sincronizar usuario desde Firebase: ${error.message}")
                }
            })

            // Obtener los datos del usuario de la base de datos local
            val usuarioLocal = TecniAppDatabase.getUser()  // Asume que getUser() ya retorna un solo objeto

            // Si se encuentra el usuario en la base de datos local, sincronizarlo con Firebase
            usuarioLocal?.let {
                val usuarioMap = mapOf(
                    "agencia" to it.agencia,
                    "apellidos" to it.apellidos,
                    "cedula" to it.cedula,
                    "email" to it.email,
                    "nombre" to it.nombre,
                    "placaVehiculo" to it.placaVehiculo,
                    "subregion" to it.subregion,
                    "telefono" to it.telefono
                )

                usuariosRef.child(it.cedula).setValue(usuarioMap)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            println("Usuario sincronizado con éxito en Firebase.")
                        } else {
                            println("Error al sincronizar usuario en Firebase: ${task.exception?.message}")
                        }
                    }
            } ?: run {
                println("No se encontró un usuario local para sincronizar.")
            }
        } else {
            println("No hay un usuario autenticado.")
        }
    }



}
