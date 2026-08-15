package com.rotacerta.entregador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rotacerta.entregador.data.DeliveryStatus
import com.rotacerta.entregador.domain.LatLng
import com.rotacerta.entregador.ui.components.ReorderableStopList
import com.rotacerta.entregador.ui.components.RouteMap
import com.rotacerta.entregador.ui.components.StopGroup
import com.rotacerta.entregador.ui.theme.*
import com.rotacerta.entregador.viewmodel.RotaViewModel

/**
 * Aba "Mapa": mostra a rota inteira no mapa nativo (com zoom livre, pinça pra ampliar) e,
 * ao tocar em "Editar sequência", abre uma lista de paradas com puxador pra arrastar e
 * reordenar manualmente.
 *
 * O mapa fica em TELA CHEIA o tempo todo (nunca muda de tamanho), e o cabeçalho + a lista
 * de edição ficam FLUTUANDO por cima dele (mesmo padrão do Google Maps/Uber), em vez de
 * dividir espaço com ele numa Column com peso. Isso não é só estético: um MapView nativo
 * dentro de uma Column com `weight` que muda de valor (como tínhamos antes) podia entrar
 * em conflito com o próprio remedição interna do mapa durante o gesto de zoom, fazendo o
 * cabeçalho "sumir" até trocar de aba. Com o mapa sempre em tamanho fixo (fillMaxSize),
 * esse conflito de medição não tem mais como acontecer.
 */
@Composable
fun MapScreen(viewModel: RotaViewModel) {
    val deliveries by viewModel.deliveries.collectAsState()
    val config by viewModel.config.collectAsState()

    val pendentes = remember(deliveries) {
        deliveries.filter { it.status == DeliveryStatus.PENDENTE }.sortedBy { it.order }
    }

    var stops by remember(pendentes) {
        mutableStateOf(
            pendentes.groupBy { it.order }.entries.sortedBy { it.key }
                .map { StopGroup(it.key, it.value) }
        )
    }
    var editMode by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var highlightOrder by remember { mutableStateOf<Int?>(null) }

    val origin = config.originLat?.let { lat -> config.originLng?.let { lng -> LatLng(lat, lng) } }
    val returnPoint = config.homeLat?.let { lat -> config.homeLng?.let { lng -> LatLng(lat, lng) } } ?: origin

    Box(Modifier.fillMaxSize()) {
        // Mapa em tela cheia, sempre do mesmo tamanho — nunca reparte espaço com o resto.
        if (stops.isEmpty()) {
            Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
                Text(
                    "Sem paradas pendentes pra mostrar.\nAdicione ou importe entregas na aba Rota.",
                    color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            RouteMap(
                deliveries = stops.flatMapIndexed { idx, group -> group.deliveries.map { it.copy(order = idx + 1) } },
                origin = origin,
                returnPoint = returnPoint,
                roundTrip = config.roundTrip,
                highlightOrder = highlightOrder,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Cabeçalho flutuando por cima do mapa (fundo sólido pra ficar legível).
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Bg)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Mapa da rota", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextMain)
                    Text(
                        if (stops.isEmpty()) "Nenhuma parada pendente" else "${stops.size} paradas · ${pendentes.size} entregas",
                        color = Muted, style = MaterialTheme.typography.bodySmall
                    )
                }
                if (stops.size >= 2) {
                    FilterChip(
                        selected = editMode,
                        onClick = {
                            if (editMode && dirty) {
                                stops = pendentes.groupBy { it.order }.entries.sortedBy { it.key }.map { StopGroup(it.key, it.value) }
                                dirty = false
                            }
                            editMode = !editMode
                            highlightOrder = null
                        },
                        label = { Text(if (editMode) "Concluir" else "Editar sequência") },
                        leadingIcon = {
                            Icon(
                                if (editMode) Icons.Default.Check else Icons.Default.DragHandle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        // Lista de edição flutuando por cima do mapa, ancorada embaixo.
        if (editMode && stops.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .background(Bg)
            ) {
                HorizontalDivider(color = Line)
                ReorderableStopList(
                    stops = stops,
                    onReorder = { newList -> stops = newList; dirty = true },
                    onStopTap = { order -> highlightOrder = if (highlightOrder == order) null else order },
                    modifier = Modifier.weight(1f)
                )
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            stops = pendentes.groupBy { it.order }.entries.sortedBy { it.key }.map { StopGroup(it.key, it.value) }
                            dirty = false
                        },
                        enabled = dirty,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Desfazer") }
                    Button(
                        onClick = {
                            viewModel.reorderStops(stops.map { it.deliveries })
                            dirty = false
                            editMode = false
                        },
                        enabled = dirty,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Salvar sequência", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
