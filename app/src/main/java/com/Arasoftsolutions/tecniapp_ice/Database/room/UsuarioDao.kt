package com.Arasoftsolutions.tecniapp_ice.Database.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.Arasoftsolutions.tecniapp_ice.Database.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsuarioDao {

    @Query("SELECT * FROM usuarios WHERE cedula = :cedula LIMIT 1")
    suspend fun getByCedula(cedula: String): UserEntity?

    @Query("SELECT * FROM usuarios WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): UserEntity?

    @Query("SELECT * FROM usuarios WHERE uid = :uid LIMIT 1")
    fun observeByUid(uid: String): Flow<UserEntity?>

    @Query("SELECT * FROM usuarios ORDER BY nombre")
    suspend fun getAll(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(users: List<UserEntity>)

    @Query("DELETE FROM usuarios")
    suspend fun clear()

    @Query("SELECT * FROM usuarios WHERE uid = :uid LIMIT 1")
    suspend fun get(uid: String): UserEntity?

}
