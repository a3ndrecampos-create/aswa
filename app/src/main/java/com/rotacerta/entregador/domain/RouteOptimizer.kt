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

    private const val STOP_MINUTES = 4.0
    private const val SPEED_KMH = 25.0
    private const val TIME_BUDGET_NANOS = 1_500_000_000L

    /**
     * Reordena as entregas pendentes. Estratégia (leve o bastante pro celular):
     *  1. Matriz de distâncias (haversine) calculada uma vez só.
     *  2. Várias soluções iniciais (vizinho mais próximo partindo de pontos diferentes).
     *  3. Cada uma é refinada com 2-opt + or-opt (move trechos de 1 a 3 paradas, inclusive
     *     invertidos) até não melhorar mais, ou até estourar 1,5 s.
     *  4. Fica a melhor de todas.
     * O custo = distância total + penalidades: prioridade ALTA tende a vir antes, e entrega
     * com horário limite (deadline) atrasada pela ETA estimada é fortemente penalizada.
     * Com FARTHEST_FIRST a rota é montada terminando no ponto de partida e depois
     * invertida (sai da mais distante, volta pra perto de casa).
     * Com roundTrip = true soma também a perna de volta até returnPoint.
     * Retorna a lista com `order` atualizado (1-based); paradas no mesmo prédio dividem número.
     */
    data class OptimizeResult(
        val deliveries: List<Delivery>,
        val distanceKm: Double,
        val baselineKm: Double,
        val usedRoads: Boolean
    )

    /** Pontos de ancoragem (início, fim) usados pela otimização, conforme as configurações. */
    fun anchors(
        origin: LatLng?, direction: RouteSortDirection, roundTrip: Boolean, returnPoint: LatLng?
    ): Pair<LatLng?, LatLng?> {
        val farthest = direction == RouteSortDirection.FARTHEST_FIRST
        val start = if (farthest) null else origin
        val end: LatLng? = when {
            farthest -> origin
            roundTrip -> returnPoint ?: origin
            else -> null
        }
        return start to end
    }

    /** Lista de pontos na ordem esperada pela matriz: paradas, início, fim (n + 2 pontos). */
    fun matrixPoints(stops: List<Delivery>, start: LatLng?, end: LatLng?): List<LatLng> {
        val filler = LatLng(stops[0].lat, stops[0].lng)
        return stops.map { LatLng(it.lat, it.lng) } + listOf(start ?: filler, end ?: filler)
    }

    fun optimize(
        pending: List<Delivery>,
        origin: LatLng?,
        direction: RouteSortDirection = RouteSortDirection.NEAREST_FIRST,
        roundTrip: Boolean = false,
        returnPoint: LatLng? = origin
    ): List<Delivery> = optimizeDetailed(pending, origin, direction, roundTrip, returnPoint).deliveries

    /**
     * Igual a [optimize], mas aceita uma matriz de distâncias reais por ruas (km, n+2 x n+2,
     * ver [matrixPoints]) e devolve também a distância total e a da ordem anterior.
     */
    fun optimizeDetailed(
        pending: List<Delivery>,
        origin: LatLng?,
        direction: RouteSortDirection = RouteSortDirection.NEAREST_FIRST,
        roundTrip: Boolean = false,
        returnPoint: LatLng? = origin,
        roadKm: Array<DoubleArray>? = null
    ): OptimizeResult {
        if (pending.size < 2) return OptimizeResult(pending, 0.0, 0.0, false)

        val farthest = direction == RouteSortDirection.FARTHEST_FIRST
        val (startAnchor, endAnchor) = anchors(origin, direction, roundTrip, returnPoint)

        val solved = solve(pending, startAnchor, endAnchor, usePenalties = !farthest, roadKm = roadKm)
        val finalOrder = if (farthest) solved.order.reversed() else solved.order

        var stopNumber = 0
        var lastLat: Double? = null
        var lastLng: Double? = null
        val numbered = finalOrder.map { d ->
            val mesmoLocal = lastLat != null && lastLng != null &&
                haversineKm(lastLat!!, lastLng!!, d.lat, d.lng) * 1000 <= SAME_STOP_RADIUS_METERS
            if (!mesmoLocal) stopNumber++
            lastLat = d.lat
            lastLng = d.lng
            d.copy(order = stopNumber)
        }
        return OptimizeResult(numbered, solved.km, solved.baselineKm, roadKm != null)
    }

    private class Solved(val order: List<Delivery>, val km: Double, val baselineKm: Double)

    private fun deadlineMinutes(d: Delivery): Int? {
        val parts = d.deadline.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun solve(
        stops: List<Delivery>, start: LatLng?, end: LatLng?, usePenalties: Boolean,
        roadKm: Array<DoubleArray>? = null
    ): Solved {
        val n = stops.size
        val sIdx = n
        val eIdx = n + 1
        val lat = DoubleArray(n + 2)
        val lng = DoubleArray(n + 2)
        for (i in 0 until n) { lat[i] = stops[i].lat; lng[i] = stops[i].lng }
        start?.let { lat[sIdx] = it.lat; lng[sIdx] = it.lng }
        end?.let { lat[eIdx] = it.lat; lng[eIdx] = it.lng }
        val dist = if (roadKm != null && roadKm.size == n + 2) roadKm
        else Array(n + 2) { i -> DoubleArray(n + 2) { j -> if (i == j) 0.0 else haversineKm(lat[i], lng[i], lat[j], lng[j]) } }

        fun pathKm(r: IntArray): Double {
            var t = 0.0
            var prev = if (start != null) sIdx else -1
            for (node in r) { if (prev >= 0) t += dist[prev][node]; prev = node }
            if (end != null && prev >= 0) t += dist[prev][eIdx]
            return t
        }

        val cal = java.util.Calendar.getInstance()
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val deadlines = IntArray(n) { deadlineMinutes(stops[it]) ?: -1 }
        val prioW = DoubleArray(n) {
            if (!usePenalties) 0.0 else when (stops[it].priority) {
                com.rotacerta.entregador.data.Priority.ALTA -> 0.15
                com.rotacerta.entregador.data.Priority.MEDIA -> 0.0
                com.rotacerta.entregador.data.Priority.BAIXA -> -0.04
            }
        }
        val hasDeadlines = usePenalties && deadlines.any { it >= 0 }

        fun cost(r: IntArray): Double {
            var total = 0.0
            var km = 0.0
            var prev = if (start != null) sIdx else -1
            for (pos in r.indices) {
                val node = r[pos]
                if (prev >= 0) { val dd = dist[prev][node]; total += dd; km += dd }
                total += prioW[node] * pos
                if (hasDeadlines && deadlines[node] >= 0) {
                    val eta = nowMin + km / SPEED_KMH * 60 + pos * STOP_MINUTES
                    val late = (eta - deadlines[node]).coerceIn(0.0, 120.0)
                    total += late * 0.5
                }
                prev = node
            }
            if (end != null && prev >= 0) total += dist[prev][eIdx]
            return total
        }

        fun nearestNeighbor(first: Int): IntArray {
            val used = BooleanArray(n)
            val r = IntArray(n)
            r[0] = first; used[first] = true
            for (k in 1 until n) {
                val cur = r[k - 1]
                var best = -1; var bd = Double.MAX_VALUE
                for (j in 0 until n) if (!used[j] && dist[cur][j] < bd) { bd = dist[cur][j]; best = j }
                r[k] = best; used[best] = true
            }
            return r
        }

        val deadline = System.nanoTime() + TIME_BUDGET_NANOS

        fun improve(initial: IntArray): Pair<IntArray, Double> {
            var route = initial
            var best = cost(route)
            var improved = true
            var pass = 0
            while (improved && pass < 30 && System.nanoTime() < deadline) {
                improved = false
                pass++
                // 2-opt: inverte um trecho
                loop@ for (i in 0 until n - 1) {
                    for (j in i + 1 until n) {
                        val cand = route.copyOf()
                        var lo = i; var hi = j
                        while (lo < hi) { val t = cand[lo]; cand[lo] = cand[hi]; cand[hi] = t; lo++; hi-- }
                        val c = cost(cand)
                        if (c < best - 1e-9) { route = cand; best = c; improved = true }
                    }
                    if (System.nanoTime() > deadline) break@loop
                }
                // or-opt: move trecho de 1..3 paradas pra outra posição (normal ou invertido)
                for (len in 1..3) {
                    if (len >= n) break
                    var i = 0
                    while (i + len <= n) {
                        val seg = route.copyOfRange(i, i + len)
                        val rest = route.filterIndexed { idx, _ -> idx < i || idx >= i + len }.toIntArray()
                        var bestCand: IntArray? = null
                        var bestC = best
                        for (pos in 0..rest.size) {
                            if (pos == i) continue
                            for (rev in 0..1) {
                                val sg = if (rev == 1) seg.reversedArray() else seg
                                if (rev == 1 && len == 1) continue
                                val cand = IntArray(n)
                                System.arraycopy(rest, 0, cand, 0, pos)
                                System.arraycopy(sg, 0, cand, pos, len)
                                System.arraycopy(rest, pos, cand, pos + len, rest.size - pos)
                                val c = cost(cand)
                                if (c < bestC - 1e-9) { bestC = c; bestCand = cand }
                            }
                        }
                        if (bestCand != null) { route = bestCand; best = bestC; improved = true }
                        i++
                        if (System.nanoTime() > deadline) break
                    }
                }
            }
            return route to best
        }

        // Pontos de partida variados: os mais próximos do início (ou espalhados, sem origem)
        val ref = start ?: LatLng(lat.take(n).average(), lng.take(n).average())
        val firsts = (0 until n).sortedBy { haversineKm(ref.lat, ref.lng, lat[it], lng[it]) }
            .take(minOf(n, 6)).toMutableList()
        if (end != null) {
            // Também tenta começar pela parada mais distante do fim (rotas que terminam em casa)
            val far = (0 until n).maxByOrNull { dist[it][eIdx] }
            if (far != null && far !in firsts) firsts.add(far)
        }

        var bestRoute: IntArray? = null
        var bestCost = Double.MAX_VALUE
        for (f in firsts) {
            if (bestRoute != null && System.nanoTime() > deadline) break
            val (r, c) = improve(nearestNeighbor(f))
            if (c < bestCost) { bestCost = c; bestRoute = r }
        }
        val best = bestRoute!!
        return Solved(best.map { stops[it] }, pathKm(best), pathKm(IntArray(n) { it }))
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
