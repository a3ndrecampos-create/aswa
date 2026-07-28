package com.rotacerta.entregador.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.DeleteOutline
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (delivered) 0.dp else 2.dp)
    ) {
        Box {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (delivered) Success else Accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (delivered) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        } else {
                            Text(delivery.order.toString(), color = Accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (delivery.verified && !delivered) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .clip(CircleShape)
                                .background(Success),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check, contentDescription = "Conferido pelo scanner",
                                tint = Color.White, modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        delivery.address, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.alpha(if (delivered) 0.5f else 1f).padding(end = 22.dp)
                    )

                    if (!delivered) {
                        Row(
                            Modifier.padding(top = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PriorityDot(delivery.priority)
                            Text(fmtBRL(delivery.value), color = RouteColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (delivery.deadline.isNotBlank()) {
                                Text("· até ${delivery.deadline}", color = Muted, fontSize = 12.sp)
                            }
                        }

                        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onDelivered,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Success)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(Modifier.width(4.dp))
                                Text("Entregue", fontSize = 12.5.sp, color = Color.White)
                            }
                            OutlinedButton(
                                onClick = onNavigate,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Navegar", fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline, contentDescription = "Remover entrega",
                    modifier = Modifier.size(16.dp), tint = Muted.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun PriorityDot(priority: Priority) {
    val (label, color) = when (priority) {
        Priority.ALTA -> "Alta" to Danger
        Priority.MEDIA -> "Média" to Accent
        Priority.BAIXA -> "Baixa" to Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Default.Circle, contentDescription = null, tint = color, modifier = Modifier.size(7.dp))
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

private fun fmtBRL(v: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", v)
