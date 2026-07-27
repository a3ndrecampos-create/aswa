package com.rotacerta.entregador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotacerta.entregador.data.HistoryEntry
import com.rotacerta.entregador.ui.theme.*
import com.rotacerta.entregador.viewmodel.RotaViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoricoScreen(viewModel: RotaViewModel) {
    val history by viewModel.history.collectAsState()

    val now = Calendar.getInstance()
    val startDay = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    val startWeek = (startDay.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -get(Calendar.DAY_OF_WEEK) + 1) }
    val startMonth = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }

    var day = 0.0; var week = 0.0; var month = 0.0
    var dayCount = 0
    history.forEach { h ->
        if (h.deliveredAt >= startDay.timeInMillis) { day += h.value; dayCount++ }
        if (h.deliveredAt >= startWeek.timeInMillis) week += h.value
        if (h.deliveredAt >= startMonth.timeInMillis) month += h.value
    }

    // Agrupa por dia (Hoje / Ontem / dd de mês) pra facilitar achar uma entrega antiga
    val grouped = remember(history) {
        history.groupBy { dayLabel(it.deliveredAt) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ganhos", style = MaterialTheme.typography.labelLarge, color = Muted)
        Spacer(Modifier.height(8.dp))

        HighlightEarnCard(day, dayCount)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EarnCard("Semana", week, Modifier.weight(1f))
            EarnCard("Mês", month, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = Muted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Nenhuma entrega concluída ainda.", color = Muted)
                }
            }
        } else {
            Text("Entregas", style = MaterialTheme.typography.labelLarge, color = Muted)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                grouped.forEach { (label, entries) ->
                    item { DayHeader(label, entries.sumOf { it.value }) }
                    items(entries, key = { it.id }) { h -> HistoryRow(h) }
                    item { Spacer(Modifier.height(10.dp)) }
                }
                item { Spacer(Modifier.height(70.dp)) }
            }
        }
    }
}

private fun dayLabel(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        cal.isSameDay(today) -> "Hoje"
        cal.isSameDay(yesterday) -> "Ontem"
        else -> SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(cal.time)
            .replaceFirstChar { it.uppercase() }
    }
}

private fun Calendar.isSameDay(other: Calendar) =
    get(Calendar.YEAR) == other.get(Calendar.YEAR) && get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)

@Composable
private fun HighlightEarnCard(value: Double, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Accent)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("HOJE", color = AccentInk.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(fmtBRL(value), color = AccentInk, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text(
                if (count == 1) "1 entrega concluída" else "$count entregas concluídas",
                color = AccentInk.copy(alpha = 0.75f), fontSize = 12.sp
            )
        }
        Icon(Icons.Default.Payments, contentDescription = null, tint = AccentInk.copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun EarnCard(label: String, value: Double, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(12.dp)
    ) {
        Text(label.uppercase(), color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(fmtBRL(value), color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun DayHeader(label: String, total: Double) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(fmtBRL(total), color = Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HistoryRow(h: HistoryEntry) {
    val hora = remember(h.deliveredAt) {
        SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(h.deliveredAt))
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Success.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(h.address, color = TextMain, fontSize = 13.sp, maxLines = 2)
            Text(hora, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text("+" + fmtBRL(h.value), color = Success, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

private fun fmtBRL(v: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", v)
