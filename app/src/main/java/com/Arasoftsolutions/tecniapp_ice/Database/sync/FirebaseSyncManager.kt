package com.Arasoftsolutions.tecniapp_ice.Database.sync

import android.content.Context
import android.util.Log
import com.Arasoftsolutions.tecniapp_ice.Database.entities.*
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import com.google.firebase.database.DataSnapshot
import java.util.Locale

/**
 * Acceso centralizado a Realtime Database en *varios* proyectos.
 *
 * Endpoints:
 * - Usuarios / verificationCodes: https://tecniapp-ice-user.firebaseio.com/
 * - Agencias / Subregiones / Vehículos: https://tecniapp-ice-datosgenerales.firebaseio.com/
 * - Localizaciones / pueblos: https://tecniapp-ice.firebaseio.com/
 * - Medidores: https://tecniapp-ice-default-rtdb.firebaseio.com/
 */
class FirebaseSyncManager(@Suppress("UNUSED_PARAMETER") context: Context) {

    private val dbUsers: DatabaseReference by lazy {
        database("https://tecniapp-ice-user.firebaseio.com")
    }

    private val dbDatosGenerales: DatabaseReference by lazy {
        database("https://tecniapp-ice-datosgenerales.firebaseio.com")
    }

    private val dbLocal: DatabaseReference by lazy {
        database("https://tecniapp-ice.firebaseio.com")
    }

    private val dbMedidores: DatabaseReference by lazy {
        database("https://tecniapp-ice-default-rtdb.firebaseio.com")
    }

    private val dbTecnicos: DatabaseReference by lazy {
        database("https://tecniapp-ice-personal.firebaseio.com/")
    }

    private val dbMaterialesIce: DatabaseReference by lazy {
        database("https://tecniapp-ice-materiales.firebaseio.com/")
    }

    private fun database(url: String): DatabaseReference {
        return runCatching { FirebaseDatabase.getInstance(url).reference }
            .getOrElse { throwable ->
                Log.e(TAG, "Error inicializando FirebaseDatabase", throwable)
                throw IllegalStateException("No se pudo inicializar la base de datos en $url", throwable)
            }
    }

    // --- USUARIOS ---
    suspend fun obtenerUsuario(uid: String): UserEntity? {
        val snap = dbUsers.child("usuarios").child(uid).get().await()
        return snap.getValue(UserEntity::class.java)
    }

    suspend fun upsertUsuarioConEmail(uid: String, email: String, otrosCampos: Map<String, Any?> = emptyMap()) {
        val emailLower = email.trim().lowercase()
        val base = mapOf(
            "uid" to uid,
            "email" to email,
            "email_lower" to emailLower
        )
        val payload = base + otrosCampos
        dbUsers.child("usuarios").child(uid).updateChildren(payload).await()
    }

    suspend fun buscarUsuarioPorEmail(email: String): UserEntity? {
        val emailLower = email.trim().lowercase()
        val snap = dbUsers.child("usuarios")
            .orderByChild("email_lower")
            .equalTo(emailLower)
            .limitToFirst(1)
            .get()
            .await()

        val first = snap.children.firstOrNull() ?: return null
        return first.getValue(UserEntity::class.java)
    }

    // --- DATOS GENERALES (Regiones / Agencias / Subregiones / Vehículos) ---
    suspend fun obtenerRegiones(): List<RegionEntity> {
        val snap = dbDatosGenerales.child("regiones").get().await()
        return snap.children.mapNotNull { child ->
            val id = child.stringChild("id") ?: child.key ?: return@mapNotNull null
            val nombre = child.stringChild("nombre") ?: return@mapNotNull null
            val trimmedId = id.trim()
            if (trimmedId.isEmpty()) return@mapNotNull null
            RegionEntity(id = trimmedId, nombre = nombre.trim())
        }
    }

    suspend fun obtenerAgencias(subregionId: String? = null): List<AgenciaEntity> {
        val snap = dbDatosGenerales.child("agencias").get().await()
        val filtroSubregion = subregionId?.trim()?.takeIf { it.isNotEmpty() }
        val regionPorSubregion = if (filtroSubregion != null) {
            runCatching {
                obtenerSubregiones().associate { it.id.lowercase(Locale.getDefault()) to it.regionId }
            }.getOrDefault(emptyMap())
        } else emptyMap()
        return snap.children.mapNotNull { child ->
            val id = child.stringChild("id") ?: child.key
            val nombre = child.stringChild("nombre") ?: return@mapNotNull null
            val regionId = child.stringChild("region_id")
                ?: child.stringChild("regionId")
                ?: child.stringChild("region")
            val subregion = child.stringChild("subregion")
                ?: child.stringChild("subregion_id")
                ?: child.stringChild("subregionId")
            val entityId = (id ?: nombre).trim()
            if (entityId.isEmpty()) return@mapNotNull null
            AgenciaEntity(
                id = entityId,
                nombre = nombre.trim(),
                regionId = regionId?.trim(),
                subregion = subregion?.trim()
            )
        }.filter { agency ->
            if (filtroSubregion == null) {
                true
            } else {
                val matchesSubregion = agency.subregion?.equals(filtroSubregion, ignoreCase = true) == true
                val regionOfSub = regionPorSubregion[filtroSubregion.lowercase(Locale.getDefault())]
                val matchesRegion = regionOfSub != null && agency.regionId?.equals(regionOfSub, ignoreCase = true) == true
                matchesSubregion || matchesRegion
            }
        }
    }

    suspend fun obtenerVehiculos(subregionId: String? = null): List<VehiculosEntity> {
        val snap = dbDatosGenerales.child("vehiculos").get().await()
        val filtroSubregion = subregionId?.trim()?.takeIf { it.isNotEmpty() }
        return snap.children.mapNotNull { child ->
            val idValue = child.stringChild("id") ?: child.key
            val agencia = child.stringChild("agencia") ?: return@mapNotNull null
            val tipo = child.stringChild("tipo") ?: ""
            val subregion = child.stringChild("subregion")
                ?: child.stringChild("subregion_id")
                ?: child.stringChild("subregionId")
            val placaRaw = child.child("placa").value
            val placa = when (placaRaw) {
                is Long -> placaRaw
                is Int -> placaRaw.toLong()
                is Double -> placaRaw.toLong()
                is String -> placaRaw.trim().toLongOrNull()
                else -> null
            } ?: return@mapNotNull null
            val entityId = idValue?.toIntOrNull()
                ?: idValue?.hashCode()
                ?: "${agencia.trim()}_${placa}".hashCode()
            VehiculosEntity(
                id = entityId,
                agencia = agencia.trim(),
                placa = placa,
                tipo = tipo.trim(),
                subregion = subregion?.trim()
            )
        }.filter { vehiculo ->
            filtroSubregion == null || vehiculo.subregion?.equals(filtroSubregion, ignoreCase = true) == true
        }
    }

    suspend fun obtenerSubregiones(): List<SubregionesEntity> {
        val snap = dbDatosGenerales.child("subregiones").get().await()
        return snap.children.mapNotNull { child ->
            val id = child.stringChild("id") ?: child.key ?: return@mapNotNull null
            val nombre = child.stringChild("nombre") ?: return@mapNotNull null
            val regionId = child.stringChild("region_id")
                ?: child.stringChild("regionId")
                ?: child.stringChild("region")
                ?: ""
            val trimmedId = id.trim()
            if (trimmedId.isEmpty()) return@mapNotNull null
            SubregionesEntity(
                id = trimmedId,
                nombre = nombre.trim(),
                regionId = regionId.trim()
            )
        }
    }

    // --- LOCALIZACIONES / PUEBLOS ---
    suspend fun obtenerLocalizaciones(subregionId: String): List<LocalizacionesEntity> {
        val node = if (dbLocal.child("Localizaciones").get().await().exists()) {
            "Localizaciones"
        } else {
            "localizaciones"
        }

        val snap = dbLocal.child(node).get().await()
        return snap.children.mapNotNull { it.getValue(LocalizacionesEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    suspend fun obtenerPueblos(subregionId: String): List<PueblosEntity> {
        val node = if (dbLocal.child("pueblos").get().await().exists()) "pueblos" else "Pueblos"
        val snap = dbLocal.child(node).get().await()
        return snap.children.mapNotNull { it.getValue(PueblosEntity::class.java) }
            .filter { it.subregion == subregionId }
    }

    // --- MEDIDORES (Sync completa) ---
    suspend fun obtenerMedidores(subregion: String): List<MedidorEntity> {
        val ruta = "Medidores/Medidores/SubRegion $subregion"
        val snap = dbMedidores.child(ruta).get().await()

        val list = mutableListOf<MedidorEntity>()

        for (grupo in snap.children) {
            for (medidor in grupo.children) {
                try {
                    val entity = medidor.getValue(MedidorEntity::class.java)
                    if (entity != null) {
                        list.add(entity.copy(subregion = subregion))
                    }
                } catch (e: Exception) {
                    Log.e("SYNC", "🛑 Error MedidorEntity: ${e.message}")
                    Log.e("SYNC", "⛔ Data: ${medidor.value}")
                }
            }
        }

        return list
    }

    // --- MEDIDOR (Búsqueda puntual de uno solo) ---
    suspend fun buscarMedidorEnFirebase(subregion: String, medidorNumber: String): MedidorEntity? {
        val ruta = "Medidores/Medidores/SubRegion $subregion"
        val snap = dbMedidores.child(ruta).child(medidorNumber).get().await()

        return try {
            val entity = snap.getValue(MedidorEntity::class.java)
            entity?.copy(subregion = subregion)
        } catch (e: Exception) {
            Log.e("SYNC", "🛑 Error MedidorEntity único: ${e.message}")
            null
        }
    }

    // --- TÉCNICOS ---
    suspend fun obtenerTecnicos(): List<TecnicoEntity> {
        val root = dbTecnicos.get().await()
        val nodo = when {
            root.hasChild("personal") -> root.child("personal")
            else -> root
        }
        return nodo.children.mapNotNull { child ->
            val cedula = child.key?.trim().orEmpty()
            val nombre = child.child("nombre").value?.toString()?.trim().orEmpty()
            if (cedula.isBlank() || nombre.isBlank()) return@mapNotNull null
            TecnicoEntity(cedula = cedula, nombre = nombre)
        }
    }

    // --- MATERIALES ---
    suspend fun obtenerMaterialesCatalogo(): List<MaterialEntity> {
        val snap = dbMaterialesIce.get().await()
        return snap.children.mapNotNull { child ->
            val codigo = child.key?.trim().orEmpty()
            val nombre = child.child("Nombre").value?.toString()?.trim().orEmpty()
            if (codigo.isBlank() || nombre.isBlank()) return@mapNotNull null
            val unidad = child.child("Unidad").value?.toString()?.trim().orEmpty()
            MaterialEntity(
                id = codigo.hashCode().toLong(),
                codigo = codigo,
                descripcion = nombre,
                unidad = unidad
            )
        }
    }

    private fun DataSnapshot.stringChild(name: String): String? =
        child(name).getValue(String::class.java)?.takeIf { it.isNotBlank() }
}

private const val TAG = "FirebaseSyncManager"
