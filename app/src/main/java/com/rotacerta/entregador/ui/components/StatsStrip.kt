package com.rotacerta.entregador.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotacerta.entregador.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatsStrip(pendingCount: Int, distanceKm: Double, etaMillis: Long?) {
    val etaText = etaMillis?.let { SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(it)) } ?: "--:--"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard("$pendingCount", "Pendentes", Modifier.weight(1f))
        StatCard(String.format(Locale("pt", "BR"), "%.1f km", distanceKm), "Distância", Modifier.weight(1f))
        StatCard(etaText, "Previsão", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(10.dp)
    ) {
        Text(value, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label.uppercase(), color = Muted, fontSize = 10.5.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
