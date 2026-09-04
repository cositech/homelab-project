package com.homelab.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.homelab.app.data.local.entity.ServiceInstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceInstanceDao {
    @Query("SELECT * FROM service_instances ORDER BY type ASC, label ASC, id ASC")
    fun observeAll(): Flow<List<ServiceInstanceEntity>>

    @Query("SELECT * FROM service_instances ORDER BY type ASC, label ASC, id ASC")
    suspend fun getAll(): List<ServiceInstanceEntity>

    @Query("SELECT * FROM service_instances WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ServiceInstanceEntity?

    @Query("SELECT tenantRef FROM service_instances WHERE id = :id COLLATE NOCASE LIMIT 1")
    suspend fun getTenantRefById(id: String): String?

    @Query("SELECT * FROM service_instances WHERE type = :type ORDER BY label ASC, id ASC")
    suspend fun getByType(type: String): List<ServiceInstanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ServiceInstanceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ServiceInstanceEntity>)

    @Query("DELETE FROM service_instances WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM service_instances")
    suspend fun deleteAll()
}
