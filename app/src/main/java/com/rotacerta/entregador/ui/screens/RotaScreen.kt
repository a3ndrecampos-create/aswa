package com.rotacerta.entregador.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.rotacerta.entregador.data.DeliveryStatus
import com.rotacerta.entregador.domain.RouteOptimizer
import com.rotacerta.entregador.ui.components.DeliveryCard
import com.rotacerta.entregador.ui.components.StatsStrip
import com.rotacerta.entregador.viewmodel.RotaViewModel
import com.rotacerta.entregador.data.NavApp

@Composable
fun RotaScreen(viewModel: RotaViewModel, onAddClick: () -> Unit) {
    val deliveries by viewModel.deliveries.collectAsState()
    val config by viewModel.config.collectAsState()
    val context = LocalContext.current
    var showPackageScanner by remember { mutableStateOf(false) }

    if (showPackageScanner) {
        CepScannerDialog(
            onResult = { cep, numero -> showPackageScanner = false; viewModel.scanPackageByLabel(cep, numero) },
            onDismiss = { showPackageScanner = false }
        )
    }

    val pending = remember(deliveries) { deliveries.filter { it.status == DeliveryStatus.PENDENTE }.sortedBy { it.order } }
    val done = remember(deliveries) { deliveries.filter { it.status == DeliveryStatus.ENTREGUE } }
    val stats = viewModel.routeStats()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        StatsStrip(stats.pendingCount, stats.distanceKm, stats.etaMillis)

        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onAddClick, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Adicionar")
            }
            OutlinedButton(onClick = { viewModel.optimizeRoute() }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Otimizar")
            }
        }
        OutlinedButton(
            onClick = { showPackageScanner = true },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Escanear etiqueta")
        }
        OutlinedButton(
            onClick = {
                val last = pending.lastOrNull() ?: return@OutlinedButton
                val originLat = config.originLat
                val originLng = config.originLng
                val destination: String
                val waypointStops: List<com.rotacerta.entregador.data.Delivery>
                if (config.roundTrip && originLat != null && originLng != null) {
                    destination = "$originLat,$originLng"
                    waypointStops = pending
                } else {
                    destination = "${last.lat},${last.lng}"
                    waypointStops = pending.dropLast(1)
                }
                val waypoints = waypointStops.joinToString("|") { "${it.lat},${it.lng}" }
                var url = "https://www.google.com/maps/dir/?api=1&destination=$destination&travelmode=driving"
                if (!(config.roundTrip && originLat != null)) {
                    originLat?.let { lat -> originLng?.let { lng -> url += "&origin=$lat,$lng" } }
                }
                if (waypoints.isNotBlank()) url += "&waypoints=" + java.net.URLEncoder.encode(waypoints, "UTF-8")
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = pending.isNotEmpty()
        ) {
            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (config.roundTrip) "Abrir rota completa (ida e volta)" else "Abrir rota completa")
        }

        if (deliveries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma entrega na rota. Toque em \"Adicionar\" para começar.", color = com.rotacerta.entregador.ui.theme.Muted)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pending.isNotEmpty()) {
                    item { SectionLabel("Pendentes (${pending.size})") }
                    items(pending, key = { it.id }) { d ->
                        DeliveryCard(
                            delivery = d,
                            onDelivered = { viewModel.markDelivered(d) },
                            onRemove = { viewModel.removeDelivery(d) },
                            onNavigate = {
                                val url = if (config.navApp == NavApp.WAZE)
                                    "https://waze.com/ul?ll=${d.lat},${d.lng}&navigate=yes"
                                else
                                    "https://www.google.com/maps/dir/?api=1&destination=${d.lat},${d.lng}&travelmode=driving"
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()))
                            }
                        )
                    }
                }
                if (done.isNotEmpty()) {
                    item { SectionLabel("Entregues (${done.size})") }
                    items(done, key = { it.id }) { d ->
                        DeliveryCard(delivery = d, onDelivered = {}, onRemove = { viewModel.removeDelivery(d) }, onNavigate = {})
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = com.rotacerta.entregador.ui.theme.Muted,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
