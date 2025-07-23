package com.Arasoftsolutions.tecniapp_ice.Database.sync

import com.Arasoftsolutions.tecniapp_ice.Database.Tablas.*
import com.Arasoftsolutions.tecniapp_ice.Database.TecniAppDatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * Clase que gestiona la sincronización entre Firebase y la base de datos local.
 * Proporciona métodos para sincronizar diferentes entidades.
 */
class FirebaseSyncManager(private val TecniAppDatabase: TecniAppDatabaseHelper) {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    // Referencias a las rutas de Firebase para cada entidad
    private val medidoresRef = database.getReference("Medidores/SubRegion Guapiles")
    private val localizacionesRef = database.getReference("Localizaciones")
    private val pueblosRef = database.getReference("Pueblos")
    private val subregionesRef = database.getReference("Subregiones")
    private val agenciasRef = database.getReference("Agencias")
    private val vehiculosRef = database.getReference("Vehiculos")
    private val usuariosRef = database.getReference("Usuarios")

    /**
     * Método general para sincronizar todas las entidades.
     * Este método puede ser llamado al inicio de la aplicación o bajo eventos específicos.
     */
    fun sincronizarFirebaseConLocal() {
        sincronizarAgencias()
        sincronizarLocalizaciones()
        sincronizarMedidores()
        sincronizarPueblos()
        sincronizarSubregiones()
        sincronizarVehiculos()
        sincronizarUsuarioAutenticado()


    }

    // ---------------------- ENTIDAD: AGENCIAS ----------------------

    /**
     * Sincroniza la entidad "Agencias" entre Firebase y la base de datos local.
     */
    private fun sincronizarAgencias() {
        // Sincronización desde Firebase hacia la base de datos local
        agenciasRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val agenciasList = mutableListOf<Agencias>()
                for (agenciaSnapshot in snapshot.children) {
                    val id = agenciaSnapshot.child("id").getValue(Int::class.java) ?: continue
                    val nombre = agenciaSnapshot.child("nombre").getValue(String::class.java) ?: ""
                    val subregion = agenciaSnapshot.child("subregion").getValue(String::class.java) ?: ""

                    agenciasList.add(Agencias(id, nombre, subregion))
                }
                TecniAppDatabase.insertOrUpdateAgencias(agenciasList)
            }

            override fun onCancelled(error: DatabaseError) {
                println("Error al sincronizar agencias: ${error.message}")
            }
        })

        // Sincronización desde la base de datos local hacia Firebase
        val agenciasLocales = TecniAppDatabase.getAgencias()
        agenciasLocales.forEach { agencia ->
            val agenciaMap = mapOf(
                "id" to agencia.id,
                "nombre" to agencia.nombre,
                "subregion" to agencia.subregion
            )
            agenciasRef.child(agencia.id.toString()).setValue(agenciaMap)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        println("Agencia sincronizada con éxito en Firebase.")
                    } else {
                        println("Error al sincronizar agencia en Firebase: ${task.exception?.message}")
                    }
                }
        }
    }

    // ---------------------- ENTIDAD: LOCALIZACIONES ----------------------

    /**
     * Sincroniza la entidad "Localizaciones" entre Firebase y la base de datos local.
     */
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

                    localizacionesList.add(
                        Localizaciones(id, calle, direccion, latitud, longitud, pueblo, alPoste, delPoste)
                    )
                }
                TecniAppDatabase.insertOrUpdateLocalizaciones(localizacionesList)
            }

            override fun onCancelled(error: DatabaseError) {
                println("Error al sincronizar localizaciones: ${error.message}")
            }
        })

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
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        println("Localización sincronizada con éxito en Firebase.")
                    } else {
                        println("Error al sincronizar localización en Firebase: ${task.exception?.message}")
                    }
                }
        }
    }

    // ---------------------- ENTIDAD: MEDIDORES ----------------------

    /**
     * Sincroniza la entidad "Medidores" entre Firebase y la base de datos local.
     */
    private fun sincronizarMedidores() {
        medidoresRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val medidoresList = mutableListOf<Medidores>()
                for (medidorSnapshot in snapshot.children) {
                    val id = medidorSnapshot.key?.toIntOrNull() ?: continue
                    val cliente = medidorSnapshot.child("cliente").getValue(String::class.java) ?: ""
                    val localizacion = medidorSnapshot.child("localizacion").getValue(String::class.java) ?: ""
                    val metros = medidorSnapshot.child("metros").getValue(String::class.java) ?: ""
                    val poste = medidorSnapshot.child("poste").getValue(String::class.java) ?: ""
                    val pueblo = medidorSnapshot.child("pueblo").getValue(String::class.java) ?: ""
                    val calle = medidorSnapshot.child("calle").getValue(String::class.java) ?: ""

                    medidoresList.add(Medidores(id, cliente, localizacion, metros, poste, pueblo, calle))
                }
                TecniAppDatabase.insertOrUpdateMedidores(medidoresList)
            }

            override fun onCancelled(error: DatabaseError) {
                println("Error al sincronizar medidores: ${error.message}")
            }
        })

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
            medidoresRef.child(medidor.id.toString()).setValue(medidorMap)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        println("Medidor sincronizado con éxito en Firebase.")
                    } else {
                        println("Error al sincronizar medidor en Firebase: ${task.exception?.message}")
                    }
                }
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
                val placa = vehiculoSnapshot.child("placaVihiculo").getValue(String::class.java) ?: 0
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
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserUid != null) {
            // Consulta Firebase para obtener el usuario autenticado
            val usuarioRef = usuariosRef.child(currentUserUid)

            usuarioRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Si el usuario existe en Firebase
                    if (snapshot.exists()) {
                        val id = snapshot.key?.toIntOrNull() ?: return
                        val agencia = snapshot.child("agencia").getValue(String::class.java) ?: ""
                        val apellidos = snapshot.child("apellidos").getValue(String::class.java) ?: ""
                        val cedula = snapshot.child("cedula").getValue(String::class.java) ?: ""
                        val email = snapshot.child("email").getValue(String::class.java) ?: ""
                        val nombre = snapshot.child("nombre").getValue(String::class.java) ?: ""
                        val placaVehiculo = snapshot.child("placaVehiculo").getValue(String::class.java) ?: ""
                        val subregion = snapshot.child("subregion").getValue(String::class.java) ?: ""
                        val telefono = snapshot.child("telefono").getValue(String::class.java) ?: ""

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

                usuariosRef.child(currentUserUid).setValue(usuarioMap)
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
