// ======================
// UsuarioDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity

@Dao
interface UsuarioDao {
    // Obtiene un usuario por su UID de Firebase
    @Query("SELECT * FROM usuarios WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): UserEntity?

    // Inserta o actualiza un usuario
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    // Métodos existentes para compatibilidad
    @Query("SELECT * FROM usuarios WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): UserEntity?

    @Query("SELECT * FROM usuarios")
    suspend fun getAll(): List<UserEntity>
}
