package com.rotacerta.entregador.domain

import android.content.Context
import android.net.Uri
import com.rotacerta.entregador.data.Priority

data class ImportedRow(
    val address: String,
    val priority: Priority,
    val deadline: String,
    val value: Double?,
    val lat: Double?,
    val lng: Double?,
    val sequence: Int?,
    val trackingCode: String
)

/**
 * Lê uma planilha .xlsx exportada de qualquer sistema de rotas e tenta identificar
 * as colunas relevantes por nome (aceita variações em pt/en): endereço, bairro,
 * cidade, CEP, prioridade, prazo, valor, latitude/longitude, sequência e código de
 * rastreio (usado depois pelo scanner de pacotes). Usa o SimpleXlsxReader (sem
 * dependências externas).
 */
object XlsxImporter {

    private val ADDR_KEYS = listOf("endereco", "endereço", "address", "local", "logradouro")
    private val NEIGH_KEYS = listOf("bairro", "neighborhood", "district")
    private val CITY_KEYS = listOf("cidade", "city", "localidade")
    private val CEP_KEYS = listOf("cep", "zipcode", "postal code", "postal")
    private val PRIORITY_KEYS = listOf("prioridade", "priority")
    private val DEADLINE_KEYS = listOf("prazo", "deadline", "horario", "horário")
    private val VALUE_KEYS = listOf("valor", "value")
    private val LAT_KEYS = listOf("latitude", "lat")
    private val LNG_KEYS = listOf("longitude", "lng", "long", "lon")
    private val SEQ_KEYS = listOf("sequence", "sequencia", "sequência", "stop", "ordem", "order")
    private val TRACKING_KEYS = listOf("spx tn", "tracking", "rastreio", "codigo", "código", "at id", "shipment_id", "shipment id", "tn")

    fun import(context: Context, uri: Uri, defaultValue: Double): List<ImportedRow> {
        val rows = SimpleXlsxReader.readFirstSheet(context, uri)
        if (rows.isEmpty()) return emptyList()

        val header = rows[0].map { it.trim().lowercase() }

        fun colIndex(names: List<String>): Int {
            var idx = header.indexOfFirst { it in names }
            if (idx == -1) idx = header.indexOfFirst { h -> names.any { h.contains(it) } }
            return idx
        }

        val addrIdx = colIndex(ADDR_KEYS)
        val neighIdx = colIndex(NEIGH_KEYS)
        val cityIdx = colIndex(CITY_KEYS)
        val cepIdx = colIndex(CEP_KEYS)
        val prioIdx = colIndex(PRIORITY_KEYS)
        val deadlineIdx = colIndex(DEADLINE_KEYS)
        val valueIdx = colIndex(VALUE_KEYS)
        val latIdx = colIndex(LAT_KEYS)
        val lngIdx = colIndex(LNG_KEYS)
        val seqIdx = colIndex(SEQ_KEYS)
        val trackingIdx = colIndex(TRACKING_KEYS)

        fun cellText(row: List<String>, idx: Int): String =
            if (idx == -1 || idx >= row.size) "" else row[idx].trim()

        val result = mutableListOf<ImportedRow>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            val rua = cellText(row, addrIdx)
            val bairro = cellText(row, neighIdx)
            val cidade = cellText(row, cityIdx)
            val cep = cellText(row, cepIdx)
            val fullAddress = listOf(rua, bairro, cidade, cep).filter { it.isNotBlank() }
                .joinToString(", ").ifBlank { rua }
            if (fullAddress.isBlank()) continue

            val priorityRaw = cellText(row, prioIdx).lowercase()
            val priority = when (priorityRaw) {
                "alta" -> Priority.ALTA
                "baixa" -> Priority.BAIXA
                else -> Priority.MEDIA
            }
            val value = cellText(row, valueIdx).replace(",", ".").toDoubleOrNull() ?: defaultValue
            val lat = cellText(row, latIdx).replace(",", ".").toDoubleOrNull()
            val lng = cellText(row, lngIdx).replace(",", ".").toDoubleOrNull()
            val seq = cellText(row, seqIdx).toIntOrNull()

            result.add(
                ImportedRow(
                    address = fullAddress,
                    priority = priority,
                    deadline = cellText(row, deadlineIdx),
                    value = value,
                    lat = lat,
                    lng = lng,
                    sequence = seq,
                    trackingCode = cellText(row, trackingIdx)
                )
            )
        }
        return result
    }
}
