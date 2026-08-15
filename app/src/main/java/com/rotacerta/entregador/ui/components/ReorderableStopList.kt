package com.rotacerta.entregador.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.ui.theme.*
import kotlin.math.roundToInt

/** Uma parada = um ou mais pacotes no mesmo endereço, tratados como um bloco ao reordenar. */
data class StopGroup(val order: Int, val deliveries: List<Delivery>)

private val ROW_HEIGHT = 68.dp

/**
 * Lista de paradas com DUAS formas de reordenar, pra atender tanto quem prefere um
 * jeito rápido quanto quem prefere precisão:
 *
 * 1) TOQUE: toca numa parada (aqui ou no número dela no mapa) pra selecioná-la, toca
 *    em outra pra movê-la pra lá. A seleção é compartilhada com o mapa — quem gerencia
 *    o estado é a tela-mãe (MapScreen), pra tocar no número do mapa e na lista fazerem
 *    a mesma coisa.
 * 2) ARRASTAR: segura o ícone ⠿ e arrasta.
 *
 * Ao mover, [onReorder] é chamado com a nova ordem completa. [onStopTap] dispara a
 * cada toque numa parada — quem decide se isso seleciona ou move é a tela-mãe.
 */
@Composable
fun ReorderableStopList(
    stops: List<StopGroup>,
    selectedOrder: Int?,
    onReorder: (List<StopGroup>) -> Unit,
    onStopTap: (Int) -> Unit,
    onDragStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }

    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    // pointerInput só reinicia quando sua chave muda; usar stop.order (estável durante
    // o arrasto) evita que o gesto seja interrompido a cada troca de posição na lista.
    // rememberUpdatedState garante que a callback sempre enxergue a lista mais recente
    // mesmo sem reiniciar a coroutine do gesto.
    val latestStops = rememberUpdatedState(stops)

    Column(modifier) {
        Text(
            if (selectedOrder == null)
                "Toque numa parada (aqui ou no número dela no mapa) pra selecionar, depois toque em outra pra mover ela pra lá. Ou segure o ⠿ pra arrastar."
            else
                "Agora toque na parada onde quer colocar essa entrega — ou toque nela de novo pra cancelar.",
            color = if (selectedOrder == null) Muted else Accent,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(stops, key = { it.order }) { stop ->
                val stopOrder = stop.order
                val index = stops.indexOf(stop)
                val isDragging = index == draggedIndex
                val isSelected = stopOrder == selectedOrder
                val offset by animateFloatAsState(
                    targetValue = if (isDragging) dragOffsetPx else 0f,
                    label = "dragOffset"
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = offset }
                        .shadow(if (isDragging) 6.dp else 0.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDragging) Surface3 else Surface2)
                        .let {
                            if (isSelected) it.border(BorderStroke(2.dp, Accent), RoundedCornerShape(14.dp)) else it
                        }
                        .clickable { onStopTap(stop.order) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .padding(start = 10.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Accent else Accent.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "Selecionada", tint = AccentInk, modifier = Modifier.size(16.dp))
                        } else {
                            Text("${index + 1}", color = AccentInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            stop.deliveries.first().address,
                            color = TextMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        val subtitle = if (stop.deliveries.size > 1) "${stop.deliveries.size} pacotes nesta parada" else "1 pacote"
                        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Arrastar para reordenar",
                        tint = Muted,
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .pointerInput(stopOrder) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        onDragStart()
                                        draggedIndex = latestStops.value.indexOfFirst { it.order == stopOrder }
                                        dragOffsetPx = 0f
                                    },
                                    onDragEnd = {
                                        draggedIndex = -1
                                        dragOffsetPx = 0f
                                    },
                                    onDragCancel = {
                                        draggedIndex = -1
                                        dragOffsetPx = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetPx += dragAmount.y
                                        val current = latestStops.value
                                        val from = current.indexOfFirst { it.order == stopOrder }
                                        if (from == -1) return@detectDragGesturesAfterLongPress
                                        val moveBy = (dragOffsetPx / rowHeightPx).roundToInt()
                                        val to = (from + moveBy).coerceIn(0, current.lastIndex)
                                        if (to != from) {
                                            val newList = current.toMutableList()
                                            val item = newList.removeAt(from)
                                            newList.add(to, item)
                                            dragOffsetPx -= moveBy * rowHeightPx
                                            draggedIndex = to
                                            onReorder(newList)
                                        }
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}
