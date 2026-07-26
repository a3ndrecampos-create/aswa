package com.rotacerta.entregador.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Priority { ALTA, MEDIA, BAIXA }
enum class DeliveryStatus { PENDENTE, ENTREGUE }

@Entity(tableName = "deliveries")
data class Delivery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val lat: Double,
    val lng: Double,
    val priority: Priority = Priority.MEDIA,
    val deadline: String = "",       // formato "HH:mm", opcional
    val value: Double,
    val status: DeliveryStatus = DeliveryStatus.PENDENTE,
    val order: Int = 999,
    val deliveredAt: Long? = null,   // epoch millis
    val approxLocation: Boolean = false,
    val trackingCode: String = "",   // código do pacote (Mercado Livre/Shopee), pra bater com o scanner
    val verified: Boolean = false    // marcado quando o pacote foi conferido pelo scanner de etiqueta
)
