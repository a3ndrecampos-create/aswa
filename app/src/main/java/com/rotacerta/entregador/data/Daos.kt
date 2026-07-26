package com.rotacerta.entregador.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM deliveries ORDER BY `order` ASC")
    fun observeAll(): Flow<List<Delivery>>

    @Insert
    suspend fun insert(delivery: Delivery): Long

    @Update
    suspend fun update(delivery: Delivery)

    @Update
    suspend fun updateAll(deliveries: List<Delivery>)

    @Delete
    suspend fun delete(delivery: Delivery)

    @Query("DELETE FROM deliveries")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM deliveries")
    suspend fun count(): Int

    @Query("SELECT * FROM deliveries WHERE trackingCode != '' AND trackingCode = :code LIMIT 1")
    suspend fun findByTrackingCode(code: String): Delivery?

    @Query("UPDATE deliveries SET verified = 1 WHERE id = :id")
    suspend fun markVerified(id: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY deliveredAt DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}
