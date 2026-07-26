package com.rotacerta.entregador.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.data.DeliveryStatus
import com.rotacerta.entregador.data.Priority
import com.rotacerta.entregador.ui.theme.*
import java.util.Locale

@Composable
fun DeliveryCard(
    delivery: Delivery,
    onDelivered: () -> Unit,
    onRemove: () -> Unit,
    onNavigate: () -> Unit
) {
    val delivered = delivery.status == DeliveryStatus.ENTREGUE
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (delivered) Success else Surface3),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (delivered) "✓" else delivery.order.toString(),
                color = if (delivered) Color(0xFF08210F) else TextMain,
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                delivery.address, color = TextMain, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.alpha(if (delivered) 0.55f else 1f)
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriorityBadge(delivery.priority)
                if (delivery.deadline.isNotBlank()) Badge("até ${delivery.deadline}", Muted)
                Badge(fmtBRL(delivery.value), RouteColor)
            }
            if (!delivered) {
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = onDelivered, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Entregue", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onNavigate, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Navegar", fontSize = 12.sp)
                    }
                    TextButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", modifier = Modifier.size(14.dp), tint = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: Priority) {
    val (label, color) = when (priority) {
        Priority.ALTA -> "Alta prioridade" to Danger
        Priority.MEDIA -> "Média prioridade" to Accent
        Priority.BAIXA -> "Baixa prioridade" to Muted
    }
    Badge(label, color)
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

private fun fmtBRL(v: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", v)
