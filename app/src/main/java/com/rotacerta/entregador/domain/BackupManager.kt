package com.rotacerta.entregador.domain

import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.data.DeliveryStatus
import com.rotacerta.entregador.data.HistoryEntry
import com.rotacerta.entregador.data.Priority
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import java.io.StringWriter

/**
 * Backup completo dos dados do app (entregas da rota atual + histórico de ganhos)
 * num arquivo .xml único que o entregador pode salvar onde quiser (Drive, e-mail
 * pra si mesmo, etc.) e restaurar depois — seja pra recuperar de um problema, seja
 * pra migrar de aparelho.
 */
data class BackupPayload(
    val backupVersion: Int = 1,
    val exportedAt: Long,
    val deliveries: List<Delivery>,
    val history: List<HistoryEntry>
)

object BackupManager {

    fun serialize(deliveries: List<Delivery>, history: List<HistoryEntry>): String {
        val writer = StringWriter()
        val xml = android.util.Xml.newSerializer()
        xml.setOutput(writer)
        xml.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        xml.startDocument("UTF-8", true)

        xml.startTag(null, "rotacerta-backup")
        xml.attribute(null, "version", "1")
        xml.attribute(null, "exportedAt", System.currentTimeMillis().toString())

        xml.startTag(null, "deliveries")
        deliveries.forEach { d ->
            xml.startTag(null, "delivery")
            xml.attribute(null, "id", d.id.toString())
            xml.attribute(null, "address", d.address)
            xml.attribute(null, "lat", d.lat.toString())
            xml.attribute(null, "lng", d.lng.toString())
            xml.attribute(null, "priority", d.priority.name)
            xml.attribute(null, "deadline", d.deadline)
            xml.attribute(null, "value", d.value.toString())
            xml.attribute(null, "status", d.status.name)
            xml.attribute(null, "order", d.order.toString())
            xml.attribute(null, "deliveredAt", d.deliveredAt?.toString() ?: "")
            xml.attribute(null, "approxLocation", d.approxLocation.toString())
            xml.attribute(null, "trackingCode", d.trackingCode)
            xml.attribute(null, "verified", d.verified.toString())
            xml.endTag(null, "delivery")
        }
        xml.endTag(null, "deliveries")

        xml.startTag(null, "history")
        history.forEach { h ->
            xml.startTag(null, "entry")
            xml.attribute(null, "id", h.id.toString())
            xml.attribute(null, "originalDeliveryId", h.originalDeliveryId.toString())
            xml.attribute(null, "address", h.address)
            xml.attribute(null, "value", h.value.toString())
            xml.attribute(null, "deliveredAt", h.deliveredAt.toString())
            xml.endTag(null, "entry")
        }
        xml.endTag(null, "history")

        xml.endTag(null, "rotacerta-backup")
        xml.endDocument()
        return writer.toString()
    }

    /** @throws IllegalArgumentException se o arquivo não for um backup XML válido do RotaCerta. */
    fun deserialize(xmlText: String): BackupPayload {
        val deliveries = mutableListOf<Delivery>()
        val history = mutableListOf<HistoryEntry>()
        var exportedAt = 0L
        var rootSeen = false

        try {
            val parser: XmlPullParser = android.util.Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlText))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "rotacerta-backup" -> {
                            rootSeen = true
                            exportedAt = parser.getAttributeValue(null, "exportedAt")?.toLongOrNull() ?: 0L
                        }
                        "delivery" -> deliveries.add(parseDelivery(parser))
                        "entry" -> history.add(parseHistoryEntry(parser))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: XmlPullParserException) {
            throw IllegalArgumentException("Esse arquivo não é um XML válido")
        }

        if (!rootSeen) throw IllegalArgumentException("Esse arquivo não parece ser um backup do RotaCerta")
        return BackupPayload(exportedAt = exportedAt, deliveries = deliveries, history = history)
    }

    private fun parseDelivery(parser: XmlPullParser): Delivery {
        fun attr(name: String) = parser.getAttributeValue(null, name)
        return Delivery(
            id = attr("id")?.toLongOrNull() ?: 0,
            address = attr("address") ?: "",
            lat = attr("lat")?.toDoubleOrNull() ?: 0.0,
            lng = attr("lng")?.toDoubleOrNull() ?: 0.0,
            priority = runCatching { Priority.valueOf(attr("priority") ?: "MEDIA") }.getOrDefault(Priority.MEDIA),
            deadline = attr("deadline") ?: "",
            value = attr("value")?.toDoubleOrNull() ?: 0.0,
            status = runCatching { DeliveryStatus.valueOf(attr("status") ?: "PENDENTE") }.getOrDefault(DeliveryStatus.PENDENTE),
            order = attr("order")?.toIntOrNull() ?: 999,
            deliveredAt = attr("deliveredAt")?.takeIf { it.isNotBlank() }?.toLongOrNull(),
            approxLocation = attr("approxLocation")?.toBoolean() ?: false,
            trackingCode = attr("trackingCode") ?: "",
            verified = attr("verified")?.toBoolean() ?: false
        )
    }

    private fun parseHistoryEntry(parser: XmlPullParser): HistoryEntry {
        fun attr(name: String) = parser.getAttributeValue(null, name)
        return HistoryEntry(
            id = attr("id")?.toLongOrNull() ?: 0,
            originalDeliveryId = attr("originalDeliveryId")?.toLongOrNull() ?: 0,
            address = attr("address") ?: "",
            value = attr("value")?.toDoubleOrNull() ?: 0.0,
            deliveredAt = attr("deliveredAt")?.toLongOrNull() ?: 0
        )
    }

    /** Nome de arquivo sugerido pro seletor do Android ao exportar, com a data embutida. */
    fun suggestedFileName(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale("pt", "BR"))
        return "rotacerta-backup-${fmt.format(java.util.Date())}.xml"
    }

    /**
     * CSV simples do histórico de ganhos, pra compartilhar rápido (WhatsApp, e-mail, Excel)
     * sem precisar do fluxo completo de backup/restauração. Não inclui a rota pendente —
     * é só o extrato de entregas já concluídas.
     */
    fun historyToCsv(history: List<HistoryEntry>): String {
        val fmt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR"))
        val header = "Data,Endereço,Valor"
        val rows = history.sortedByDescending { it.deliveredAt }.joinToString("\n") { h ->
            val addr = h.address.replace("\"", "'")
            "${fmt.format(java.util.Date(h.deliveredAt))},\"$addr\",${h.value}"
        }
        return "$header\n$rows"
    }
}
