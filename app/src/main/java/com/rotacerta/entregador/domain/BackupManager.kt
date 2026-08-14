package com.rotacerta.entregador.domain

import com.google.gson.GsonBuilder
import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.data.HistoryEntry

/**
 * Backup completo dos dados do app (entregas da rota atual + histórico de ganhos)
 * num arquivo .json único que o entregador pode salvar onde quiser (Drive, e-mail
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
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun serialize(deliveries: List<Delivery>, history: List<HistoryEntry>): String =
        gson.toJson(BackupPayload(exportedAt = System.currentTimeMillis(), deliveries = deliveries, history = history))

    /** @throws com.google.gson.JsonSyntaxException se o arquivo não for um backup válido do RotaCerta. */
    fun deserialize(json: String): BackupPayload {
        val payload = gson.fromJson(json, BackupPayload::class.java)
            ?: throw IllegalArgumentException("Arquivo vazio ou em formato inválido")
        if (payload.deliveries == null || payload.history == null) {
            throw IllegalArgumentException("Esse arquivo não parece ser um backup do RotaCerta")
        }
        return payload
    }

    /** Nome de arquivo sugerido pro seletor do Android ao exportar, com a data embutida. */
    fun suggestedFileName(): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale("pt", "BR"))
        return "rotacerta-backup-${fmt.format(java.util.Date())}.json"
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
