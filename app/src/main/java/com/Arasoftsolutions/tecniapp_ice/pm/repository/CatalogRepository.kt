package com.Arasoftsolutions.tecniapp_ice.pm.repository

import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.CatalogSyncMetaEntity
import com.Arasoftsolutions.tecniapp_ice.pm.model.entities.OrdenSapEntity
import com.Arasoftsolutions.tecniapp_ice.pm.room.PmDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class CatalogRepository(
    private val database: PmDatabase,
    ordenesDbUrl: String = ORDENES_DB_URL
) {
    private val ordenesDb: DatabaseReference = FirebaseDatabase.getInstance(ordenesDbUrl).reference

    suspend fun syncOrdenesCatalog(force: Boolean = false): Int {
        val metaDao = database.catalogSyncMetaDao()
        val ordenDao = database.ordenSapDao()
        val remoteVersion = fetchRemoteVersion()
        val currentMeta = metaDao.getMeta(CATALOG_ID)

        if (!force && currentMeta?.remoteVersion == remoteVersion) {
            return 0
        }

        val snap = ordenesDb.child("ordenes").get().await()
        val ordenes = snap.children.mapNotNull { child ->
            val ordenSap = child.child("ordenSap").getValue(Long::class.java)
                ?: child.child("ordenSap").getValue(String::class.java)?.toLongOrNull()
                ?: return@mapNotNull null
            val descripcion = child.child("descripcion").getValue(String::class.java).orEmpty()
            val regionKey = child.child("regionKey").getValue(String::class.java).orEmpty()
            val subregionKey = child.child("subregionKey").getValue(String::class.java).orEmpty()
            val circuitoId = child.child("circuitoId").getValue(String::class.java)
            val agenciaId = child.child("agenciaId").getValue(String::class.java)
            val modulo = child.child("modulo").getValue(String::class.java)
            val updatedAt = child.child("updatedAt").getValue(Long::class.java) ?: remoteVersion

            OrdenSapEntity(
                ordenSap = ordenSap,
                descripcion = descripcion,
                regionKey = regionKey,
                subregionKey = subregionKey,
                circuitoId = circuitoId,
                agenciaId = agenciaId,
                modulo = modulo,
                updatedAt = updatedAt
            )
        }

        ordenDao.clearAll()
        if (ordenes.isNotEmpty()) {
            ordenDao.upsertAll(ordenes)
        }

        metaDao.upsert(
            CatalogSyncMetaEntity(
                catalogId = CATALOG_ID,
                remoteVersion = remoteVersion,
                lastSyncAt = System.currentTimeMillis()
            )
        )

        return ordenes.size
    }

    private suspend fun fetchRemoteVersion(): Long {
        val snap = ordenesDb.child("meta").child("ordenesVersion").get().await()
        return snap.getValue(Long::class.java)
            ?: snap.getValue(String::class.java)?.toLongOrNull()
            ?: System.currentTimeMillis()
    }

    companion object {
        const val ORDENES_DB_URL = "https://tecniapp-ice-ordenes.firebaseio.com/"
        private const val CATALOG_ID = "ordenes_sap"
    }
}
