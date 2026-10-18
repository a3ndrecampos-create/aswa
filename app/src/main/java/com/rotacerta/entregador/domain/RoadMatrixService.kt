package com.rotacerta.entregador.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Distâncias REAIS por ruas (km) entre todos os pontos, via OSRM (dados do OpenStreetMap,
 * grátis, sem chave). Se a internet ou o servidor falharem, devolve null e o otimizador
 * usa distância em linha reta — o app nunca fica sem otimizar.
 */
object RoadMatrixService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchKm(points: List<LatLng>): Array<DoubleArray>? = withContext(Dispatchers.IO) {
        if (points.size < 2 || points.size > 100) return@withContext null
        runCatching {
            val coords = points.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.US, it.lng, it.lat) }
            val url = "https://router.project-osrm.org/table/v1/driving/$coords?annotations=distance"
            val req = Request.Builder().url(url).header("User-Agent", "FlexOtimizadorDeRota/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val json = JSONObject(resp.body?.string() ?: return@runCatching null)
                if (json.optString("code") != "Ok") return@runCatching null
                val rows = json.getJSONArray("distances")
                val n = points.size
                Array(n) { i ->
                    val row = rows.getJSONArray(i)
                    DoubleArray(n) { j ->
                        if (i == j) 0.0
                        else if (row.isNull(j)) {
                            // Sem rota entre os dois pontos: cai pra linha reta com margem.
                            RouteOptimizer.haversineKm(points[i].lat, points[i].lng, points[j].lat, points[j].lng) * 1.4
                        } else row.getDouble(j) / 1000.0
                    }
                }
            }
        }.getOrNull()
    }
}
