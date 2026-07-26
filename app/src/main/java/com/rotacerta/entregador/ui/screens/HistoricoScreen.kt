package com.rotacerta.entregador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    history.forEach { h ->
        if (h.deliveredAt >= startDay.timeInMillis) day += h.value
        if (h.deliveredAt >= startWeek.timeInMillis) week += h.value
        if (h.deliveredAt >= startMonth.timeInMillis) month += h.value
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EarnCard("Hoje", day, Modifier.weight(1f))
            EarnCard("Semana", week, Modifier.weight(1f))
            EarnCard("Mês", month, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma entrega concluída ainda.", color = Muted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.id }) { h -> HistoryRow(h) }
            }
        }
    }
}

@Composable
private fun EarnCard(label: String, value: Double, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Surface, MaterialTheme.shapes.medium)
            .padding(10.dp)
    ) {
        Text(fmtBRL(value), color = Success, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label.uppercase(), color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HistoryRow(h: HistoryEntry) {
    val when_ = remember(h.deliveredAt) {
        SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale("pt", "BR")).format(Date(h.deliveredAt))
    }
    Row(
        Modifier.fillMaxWidth()
            .background(Surface, MaterialTheme.shapes.medium)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(h.address, color = TextMain, fontSize = 13.sp)
            Text(when_, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text("+" + fmtBRL(h.value), color = Success, fontWeight = FontWeight.Bold)
    }
}

private fun fmtBRL(v: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", v)
