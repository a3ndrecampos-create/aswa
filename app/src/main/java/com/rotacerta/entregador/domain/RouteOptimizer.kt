package com.rotacerta.entregador.domain

import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.data.RouteSortDirection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

object RouteOptimizer {

    // Distância máxima (em metros) pra considerar duas entregas "no mesmo lugar"
    // (mesmo prédio, apto/bloco diferente) — usado no agrupamento de paradas.
    private const val SAME_STOP_RADIUS_METERS = 30.0

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
     * Com roundTrip = true, depois de montar a rota inicial ainda roda uma otimização
     * extra (2-opt) considerando o trajeto de volta até returnPoint (o endereço de
     * casa/destino final, se definido — senão usa o próprio ponto de partida), pra
     * evitar cruzamentos bobos que deixariam a volta mais longa que o necessário.
     * Retorna a lista já com o campo `order` atualizado (1-based).
     */
    fun optimize(
        pending: List<Delivery>,
        origin: LatLng?,
        direction: RouteSortDirection = RouteSortDirection.NEAREST_FIRST,
        roundTrip: Boolean = false,
        returnPoint: LatLng? = origin
    ): List<Delivery> {
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

        var finalOrder = if (direction == RouteSortDirection.FARTHEST_FIRST) ordered.reversed() else ordered

        val volta = returnPoint ?: origin
        if (roundTrip && volta != null && finalOrder.size in 3..80) {
            finalOrder = twoOptRoundTrip(finalOrder, volta)
        }

        // Entregas no MESMO prédio/endereço (várias encomendas pra um só lugar, mesmo
        // que sejam apto/bloco diferentes — ex: mesmo número, apto 12 e apto 34) ficam
        // sempre lado a lado na rota — aqui elas recebem o mesmo número de parada.
        // Agrupamos pela COORDENADA (não pelo texto do endereço), porque endereços com
        // o mesmo número mas complemento diferente (apto/bloco) têm texto diferente
        // mas são fisicamente o mesmo local.
        var stopNumber = 0
        var lastLat: Double? = null
        var lastLng: Double? = null
        return finalOrder.map { d ->
            val mesmoLocal = lastLat != null && lastLng != null &&
                haversineKm(lastLat!!, lastLng!!, d.lat, d.lng) * 1000 <= SAME_STOP_RADIUS_METERS
            if (!mesmoLocal) stopNumber++
            lastLat = d.lat
            lastLng = d.lng
            d.copy(order = stopNumber)
        }
    }

    // Melhoria local (2-opt) pro trajeto fechado origem -> paradas -> origem: troca
    // trechos de posição enquanto isso reduzir a distância total do loop.
    private fun twoOptRoundTrip(stops: List<Delivery>, origin: LatLng): List<Delivery> {
        fun loopDistance(r: List<Delivery>): Double {
            var d = haversineKm(origin.lat, origin.lng, r.first().lat, r.first().lng)
            for (i in 0 until r.size - 1) d += haversineKm(r[i].lat, r[i].lng, r[i + 1].lat, r[i + 1].lng)
            d += haversineKm(r.last().lat, r.last().lng, origin.lat, origin.lng)
            return d
        }

        var route = stops.toMutableList()
        var bestDist = loopDistance(route)
        var improved = true
        var pass = 0
        while (improved && pass < 20) {
            improved = false
            pass++
            for (i in 0 until route.size - 1) {
                for (j in i + 1 until route.size) {
                    val candidate = route.toMutableList()
                    var lo = i; var hi = j
                    while (lo < hi) {
                        val tmp = candidate[lo]; candidate[lo] = candidate[hi]; candidate[hi] = tmp
                        lo++; hi--
                    }
                    val candidateDist = loopDistance(candidate)
                    if (candidateDist < bestDist - 1e-9) {
                        route = candidate
                        bestDist = candidateDist
                        improved = true
                    }
                }
            }
        }
        return route
    }

    data class RouteStats(val pendingCount: Int, val distanceKm: Double, val etaMillis: Long?)

    fun computeStats(pending: List<Delivery>, origin: LatLng?, avgSpeedKmh: Double, roundTrip: Boolean = false, returnPoint: LatLng? = origin): RouteStats {
        val sorted = pending.sortedBy { it.order }
        var dist = 0.0
        var current = origin ?: sorted.firstOrNull()?.let { LatLng(it.lat, it.lng) }
        sorted.forEach { d ->
            current?.let { dist += haversineKm(it.lat, it.lng, d.lat, d.lng) }
            current = LatLng(d.lat, d.lng)
        }
        val volta = returnPoint ?: origin
        if (roundTrip && volta != null && sorted.isNotEmpty()) {
            val last = sorted.last()
            dist += haversineKm(last.lat, last.lng, volta.lat, volta.lng)
        }
        val travelMin = (dist / avgSpeedKmh) * 60
        val stopMin = sorted.size * 4.0 // tempo médio por parada
        val totalMin = travelMin + stopMin
        val eta = if (sorted.isNotEmpty()) System.currentTimeMillis() + (totalMin * 60_000).toLong() else null
        return RouteStats(sorted.size, dist, eta)
    }
}
