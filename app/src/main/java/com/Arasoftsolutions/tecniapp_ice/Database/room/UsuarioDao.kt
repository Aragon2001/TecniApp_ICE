// ======================
// UsuarioDao.kt
// ======================
package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.*
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity

@Dao
interface UsuarioDao {
    // Inserta o actualiza el usuario individualmente
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity)

    // Busca un usuario por email
    @Query("SELECT * FROM usuarios WHERE email = :email LIMIT 1")
    suspend fun getByEmail(email: String): UserEntity?

    // Devuelve todos los usuarios guardados
    @Query("SELECT * FROM usuarios")
    suspend fun getAll(): List<UserEntity>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsert(user: UserEntity)

}
