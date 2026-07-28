package com.rotacerta.entregador.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotacerta.entregador.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StatsStrip(pendingCount: Int, distanceKm: Double, etaMillis: Long?) {
    val etaText = etaMillis?.let { SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(it)) } ?: "--:--"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(Icons.Default.Assignment, "$pendingCount", "Pendentes", Modifier.weight(1f))
        StatCard(Icons.Default.Place, String.format(Locale("pt", "BR"), "%.1f km", distanceKm), "Distância", Modifier.weight(1f))
        StatCard(Icons.Default.AccessTime, etaText, "Previsão", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
            }
            Text(value, color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(label.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 1.dp))
        }
    }
}
