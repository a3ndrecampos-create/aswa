package com.rotacerta.entregador.ui.screens

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import com.rotacerta.entregador.data.DeliveryStatus
import com.rotacerta.entregador.domain.LatLng
import com.rotacerta.entregador.domain.MapHtmlBuilder
import com.rotacerta.entregador.ui.components.ReorderableStopList
import com.rotacerta.entregador.ui.components.StopGroup
import com.rotacerta.entregador.ui.theme.*
import com.rotacerta.entregador.viewmodel.RotaViewModel

/**
 * Aba "Mapa": mostra a rota inteira no mapa (com zoom livre, pinça pra ampliar,
 * igual ao diálogo rápido) e, ao tocar em "Editar sequência", abre uma lista de
 * paradas com puxador pra arrastar e reordenar manualmente — útil quando o
 * entregador conhece um atalho que o otimizador automático não sabe.
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

    val mapHtml = remember(stops, highlightOrder, origin, returnPoint, config.roundTrip) {
        val renumbered = stops.flatMapIndexed { idx, group -> group.deliveries.map { it.copy(order = idx + 1) } }
        MapHtmlBuilder.build(renumbered, origin, returnPoint, config.roundTrip, highlightOrder)
    }

    Column(Modifier.fillMaxSize()) {
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
                            // saindo do modo edição sem salvar -> descarta o rascunho
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

        Box(
            Modifier
                .fillMaxWidth()
                .weight(if (editMode) 0.4f else 1f)
        ) {
            if (stops.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sem paradas pendentes pra mostrar.\nAdicione ou importe entregas na aba Rota.",
                        color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // key(mapHtml) faz o Compose recriar o WebView (chamando factory de novo)
                // só quando o HTML realmente muda (reordenar, tocar numa parada). Sem isso,
                // o `update` do AndroidView roda a CADA recomposição da tela — inclusive
                // uma logo depois da primeira criação — recarregando a página repetidas
                // vezes e fazendo o mapa nunca terminar de carregar (fica preso em
                // "Carregando mapa...", que na prática parece uma tela branca).
                key(mapHtml) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(android.graphics.Color.parseColor("#0F1115"))
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/120.0.0.0 Mobile Safari/537.36"
                                webViewClient = WebViewClient()
                                loadDataWithBaseURL("https://rotacerta.app/", mapHtml, "text/html", "UTF-8", null)
                            }
                        }
                    )
                }
            }
        }

        if (editMode && stops.isNotEmpty()) {
            HorizontalDivider(color = Line)
            Text(
                "Segure o ícone ⠿ e arraste para mudar a ordem das paradas",
                color = Muted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ReorderableStopList(
                stops = stops,
                onReorder = { newList -> stops = newList; dirty = true },
                onStopTap = { order -> highlightOrder = if (highlightOrder == order) null else order },
                modifier = Modifier.weight(0.6f)
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
