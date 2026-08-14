package com.rotacerta.entregador.ui.screens

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rotacerta.entregador.data.DeliveryStatus
import com.rotacerta.entregador.domain.LatLng
import com.rotacerta.entregador.domain.MapHtmlBuilder
import com.rotacerta.entregador.ui.components.ReorderableStopList
import com.rotacerta.entregador.ui.components.StopGroup
import com.rotacerta.entregador.ui.theme.*
import com.rotacerta.entregador.viewmodel.RotaViewModel
import kotlinx.coroutines.delay

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
                // reloadTick força uma nova tentativa quando o usuário toca em "Tentar de
                // novo" sem precisar que o conteúdo do mapa mude.
                var reloadTick by remember { mutableStateOf(0) }
                var loadFailed by remember(mapHtml, reloadTick) { mutableStateOf(false) }
                var loaded by remember(mapHtml, reloadTick) { mutableStateOf(false) }

                // Rede de segurança: antes a página só avisava erro via JavaScript (ex.:
                // window.onerror), que NÃO é acionado quando um recurso externo (o script do
                // Leaflet, os tiles do mapa) simplesmente falha em carregar/é bloqueado pela
                // rede — nesses casos a tela ficava em branco sem nenhum aviso. Agora o
                // Android detecta a falha diretamente (onReceivedError), e um cronômetro de
                // segurança também cobre o caso de a rede travar sem erro explícito.
                key(mapHtml, reloadTick) {
                    Box(Modifier.fillMaxSize()) {
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
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            val url = request?.url?.toString().orEmpty()
                                            // Só os recursos que o mapa realmente precisa (o
                                            // script do Leaflet ou os tiles) contam como falha —
                                            // outros erros (ex. favicon) não devem derrubar o mapa.
                                            if (url.contains("unpkg.com") || url.contains("basemaps.cartocdn.com")) {
                                                loadFailed = true
                                            }
                                        }
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            loaded = true
                                        }
                                    }
                                    loadDataWithBaseURL("https://rotacerta.app/", mapHtml, "text/html", "UTF-8", null)
                                }
                            }
                        )

                        // Cronômetro de segurança: se depois de 10s nada terminou de carregar
                        // nem deu erro explícito (rede que trava sem responder), assume falha
                        // em vez de deixar o usuário olhando pra uma tela em branco pra sempre.
                        LaunchedEffect(mapHtml, reloadTick) {
                            delay(10_000)
                            if (!loaded) loadFailed = true
                        }

                        if (loadFailed) {
                            Column(
                                Modifier.fillMaxSize().background(Bg).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.WifiOff, contentDescription = null, tint = Muted, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Não consegui carregar o mapa.\nVerifique sua conexão com a internet.",
                                    color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(onClick = {
                                    loadFailed = false
                                    loaded = false
                                    reloadTick++
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Tentar de novo")
                                }
                            }
                        }
                    }
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
