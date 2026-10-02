package com.rotacerta.entregador.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rotacerta.entregador.data.HistoryEntry
import com.rotacerta.entregador.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private enum class BucketType { DAY, WEEK, MONTH }
private data class ReportRange(val label: String, val type: BucketType, val count: Int)
private data class Bucket(val label: String, val total: Double, val count: Int)

private val RANGES = listOf(
    ReportRange("7 dias", BucketType.DAY, 7),
    ReportRange("4 semanas", BucketType.WEEK, 4),
    ReportRange("6 meses", BucketType.MONTH, 6)
)

/**
 * Relatório de ganhos em tela cheia: total do período, média por dia/entrega, melhor
 * dia/semana/mês, e um gráfico de barras simples desenhado com Canvas (sem depender de
 * lib de gráficos externa, seguindo o mesmo espírito pragmático do resto do app).
 */
@Composable
fun ReportDialog(history: List<HistoryEntry>, onDismiss: () -> Unit, onExport: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            var range by remember { mutableStateOf(RANGES[0]) }
            val buckets = remember(history, range) { buildBuckets(history, range) }
            val total = buckets.sumOf { it.total }
            val deliveryCount = buckets.sumOf { it.count }
            val avgPerDelivery = if (deliveryCount > 0) total / deliveryCount else 0.0
            val avgPerBucket = if (buckets.isNotEmpty()) total / buckets.size else 0.0
            val best = buckets.maxByOrNull { it.total }

            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp, 14.dp, 8.dp, 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Relatório de ganhos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextMain)
                    Row {
                        IconButton(onClick = onExport) {
                            Icon(Icons.Default.Share, contentDescription = "Exportar histórico em CSV", tint = Muted)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Muted)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RANGES.forEach { r ->
                        FilterChip(selected = r == range, onClick = { range = r }, label = { Text(r.label) })
                    }
                }

                Column(Modifier.weight(1f).padding(16.dp)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Accent)
                            .padding(16.dp)
                    ) {
                        Text("TOTAL NO PERÍODO", color = AccentInk.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(fmtBRL(total), color = AccentInk, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                        Text(
                            if (deliveryCount == 1) "1 entrega concluída" else "$deliveryCount entregas concluídas",
                            color = AccentInk.copy(alpha = 0.75f), fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("Média/entrega", fmtBRL(avgPerDelivery), Modifier.weight(1f))
                        MiniStat(
                            when (range.type) { BucketType.DAY -> "Média/dia"; BucketType.WEEK -> "Média/semana"; BucketType.MONTH -> "Média/mês" },
                            fmtBRL(avgPerBucket), Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (best != null && best.total > 0) {
                        MiniStat("Melhor período: ${best.label}", fmtBRL(best.total), Modifier.fillMaxWidth())
                    }

                    Spacer(Modifier.height(20.dp))
                    if (buckets.all { it.total == 0.0 }) {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            Text("Sem ganhos nesse período ainda.", color = Muted)
                        }
                    } else {
                        BarChart(buckets, Modifier.fillMaxWidth().height(180.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(12.dp)
    ) {
        Text(label.uppercase(), color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun BarChart(buckets: List<Bucket>, modifier: Modifier = Modifier) {
    val barColor = Accent
    val trackColor = Surface2
    val maxVal = (buckets.maxOfOrNull { it.total } ?: 0.0).coerceAtLeast(0.01)

    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = buckets.size
            val gap = size.width * 0.03f
            val barWidth = (size.width - gap * (n - 1)) / n
            buckets.forEachIndexed { i, b ->
                val x = i * (barWidth + gap)
                val fullHeight = size.height
                val barHeight = (b.total / maxVal).toFloat() * fullHeight
                // trilha (fundo) pra dar noção da escala mesmo em barras baixas
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, fullHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, fullHeight - barHeight),
                        size = Size(barWidth, barHeight.coerceAtLeast(6f)),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            buckets.forEach { b ->
                Text(
                    b.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = Muted, maxLines = 1
                )
            }
        }
    }
}

private fun buildBuckets(history: List<HistoryEntry>, range: ReportRange): List<Bucket> {
    val now = Calendar.getInstance()
    return (range.count - 1 downTo 0).map { i ->
        val cal = now.clone() as Calendar
        when (range.type) {
            BucketType.DAY -> cal.add(Calendar.DAY_OF_YEAR, -i)
            BucketType.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, -i)
            BucketType.MONTH -> cal.add(Calendar.MONTH, -i)
        }
        val (start, end) = boundsFor(cal, range.type)
        val entries = history.filter { it.deliveredAt in start until end }
        Bucket(labelFor(cal, range.type), entries.sumOf { it.value }, entries.size)
    }
}

private fun boundsFor(cal: Calendar, type: BucketType): Pair<Long, Long> {
    val start = cal.clone() as Calendar
    when (type) {
        BucketType.DAY -> { /* já é o dia certo */ }
        BucketType.WEEK -> start.set(Calendar.DAY_OF_WEEK, start.firstDayOfWeek)
        BucketType.MONTH -> start.set(Calendar.DAY_OF_MONTH, 1)
    }
    start.set(Calendar.HOUR_OF_DAY, 0); start.set(Calendar.MINUTE, 0)
    start.set(Calendar.SECOND, 0); start.set(Calendar.MILLISECOND, 0)
    val end = start.clone() as Calendar
    when (type) {
        BucketType.DAY -> end.add(Calendar.DAY_OF_YEAR, 1)
        BucketType.WEEK -> end.add(Calendar.WEEK_OF_YEAR, 1)
        BucketType.MONTH -> end.add(Calendar.MONTH, 1)
    }
    return start.timeInMillis to end.timeInMillis
}

private fun labelFor(cal: Calendar, type: BucketType): String = when (type) {
    BucketType.DAY -> SimpleDateFormat("EEE", Locale("pt", "BR")).format(cal.time)
        .replaceFirstChar { it.uppercase() }.take(3)
    BucketType.WEEK -> "S${cal.get(Calendar.WEEK_OF_YEAR)}"
    BucketType.MONTH -> SimpleDateFormat("MMM", Locale("pt", "BR")).format(cal.time)
        .replaceFirstChar { it.uppercase() }.trimEnd('.')
}

private fun fmtBRL(v: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", v)
