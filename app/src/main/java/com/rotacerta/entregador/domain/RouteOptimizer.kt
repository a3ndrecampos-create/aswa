package com.rotacerta.entregador.domain

import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.data.RouteSortDirection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

object RouteOptimizer {

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        fun toRad(x: Double) = x * Math.PI / 180
        val dLat = toRad(lat2 - lat1)
        val dLon = toRad(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(toRad(lat1)) * cos(toRad(lat2)) * sin(dLon / 2).let { it * it }
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Reordena as entregas pendentes usando o algoritmo do vizinho mais próximo,
     * partindo do ponto de origem configurado (ou da primeira entrega, se não houver origem).
     * Com direction = NEAREST_FIRST, a rota sai da mais próxima e vai até a mais distante.
     * Com direction = FARTHEST_FIRST, a rota é invertida: sai da mais distante e termina
     * perto do ponto de partida.
     * Retorna a lista já com o campo `order` atualizado (1-based).
     */
    fun optimize(pending: List<Delivery>, origin: LatLng?, direction: RouteSortDirection = RouteSortDirection.NEAREST_FIRST): List<Delivery> {
        if (pending.size < 2) return pending
        var current = origin ?: LatLng(pending[0].lat, pending[0].lng)
        val remaining = pending.toMutableList()
        val ordered = mutableListOf<Delivery>()

        while (remaining.isNotEmpty()) {
            var bestIdx = 0
            var bestDist = Double.MAX_VALUE
            remaining.forEachIndexed { i, d ->
                val dist = haversineKm(current.lat, current.lng, d.lat, d.lng)
                if (dist < bestDist) {
                    bestDist = dist
                    bestIdx = i
                }
            }
            val chosen = remaining.removeAt(bestIdx)
            ordered.add(chosen)
            current = LatLng(chosen.lat, chosen.lng)
        }

        val finalOrder = if (direction == RouteSortDirection.FARTHEST_FIRST) ordered.reversed() else ordered
        return finalOrder.mapIndexed { i, d -> d.copy(order = i + 1) }
    }

    data class RouteStats(val pendingCount: Int, val distanceKm: Double, val etaMillis: Long?)

    fun computeStats(pending: List<Delivery>, origin: LatLng?, avgSpeedKmh: Double): RouteStats {
        val sorted = pending.sortedBy { it.order }
        var dist = 0.0
        var current = origin ?: sorted.firstOrNull()?.let { LatLng(it.lat, it.lng) }
        sorted.forEach { d ->
            current?.let { dist += haversineKm(it.lat, it.lng, d.lat, d.lng) }
            current = LatLng(d.lat, d.lng)
        }
        val travelMin = (dist / avgSpeedKmh) * 60
        val stopMin = sorted.size * 4.0 // tempo médio por parada
        val totalMin = travelMin + stopMin
        val eta = if (sorted.isNotEmpty()) System.currentTimeMillis() + (totalMin * 60_000).toLong() else null
        return RouteStats(sorted.size, dist, eta)
    }
}
