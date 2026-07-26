package com.rotacerta.entregador.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalDeliveryId: Long,
    val address: String,
    val value: Double,
    val deliveredAt: Long // epoch millis
)
