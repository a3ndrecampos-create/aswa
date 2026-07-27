package com.rotacerta.entregador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.rotacerta.entregador.data.DeliveryStatus
import com.rotacerta.entregador.domain.RouteOptimizer
import com.rotacerta.entregador.ui.components.DeliveryCard
import com.rotacerta.entregador.ui.components.StatsStrip
import com.rotacerta.entregador.ui.theme.Accent
import com.rotacerta.entregador.ui.theme.AccentInk
import com.rotacerta.entregador.ui.theme.Muted
import com.rotacerta.entregador.viewmodel.RotaViewModel
import com.rotacerta.entregador.viewmodel.ScanLabelResult
import com.rotacerta.entregador.data.NavApp

@Composable
fun RotaScreen(viewModel: RotaViewModel, onAddClick: () -> Unit) {
    val deliveries by viewModel.deliveries.collectAsState()
    val config by viewModel.config.collectAsState()
    val context = LocalContext.current
    var showPackageScanner by remember { mutableStateOf(false) }
    val scanResult by viewModel.scanLabelResult.collectAsState()

    if (showPackageScanner) {
        TrackingScannerDialog(
            onResult = { code -> showPackageScanner = false; viewModel.scanPackageByTrackingCode(code) },
            onDismiss = { showPackageScanner = false }
        )
    }

    scanResult?.let { result ->
        ScanResultDialog(result, onDismiss = { viewModel.clearScanLabelResult() })
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
                    val gruposPorParada = remember(pending) {
                        val result = mutableListOf<Pair<Int, List<com.rotacerta.entregador.data.Delivery>>>()
                        var currentOrder: Int? = null
                        var bucket = mutableListOf<com.rotacerta.entregador.data.Delivery>()
                        pending.forEach { d ->
                            if (d.order != currentOrder) {
                                if (bucket.isNotEmpty()) result.add(currentOrder!! to bucket)
                                bucket = mutableListOf()
                                currentOrder = d.order
                            }
                            bucket.add(d)
                        }
                        if (bucket.isNotEmpty()) result.add(currentOrder!! to bucket)
                        result
                    }
                    gruposPorParada.forEach { (stopOrder, itemsNaParada) ->
                        if (itemsNaParada.size > 1) {
                            item(key = "stop-$stopOrder") { StopGroupLabel(stopOrder, itemsNaParada.size) }
                        }
                        items(itemsNaParada, key = { it.id }) { d ->
                            DeliveryCard(
                                delivery = d,
                                onDelivered = { viewModel.markDelivered(d) },
                                onRemove = { viewModel.removeDelivery(d) },
                                onNavigate = {
                                    val enderecoCodificado = java.net.URLEncoder.encode(d.address, "UTF-8")
                                    val url = if (config.navApp == NavApp.WAZE)
                                        "https://waze.com/ul?q=$enderecoCodificado&navigate=yes"
                                    else
                                        "https://www.google.com/maps/dir/?api=1&destination=$enderecoCodificado&travelmode=driving"
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()))
                                }
                            )
                        }
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
private fun StopGroupLabel(stopOrder: Int, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "📍 Parada $stopOrder",
            color = Accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Text(
            "· $count pacotes neste endereço",
            color = com.rotacerta.entregador.ui.theme.Muted,
            style = MaterialTheme.typography.labelMedium
        )
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

@Composable
private fun ScanResultDialog(result: ScanLabelResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Entendi") }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (result) {
                    is ScanLabelResult.Found -> {
                        Box(
                            Modifier.size(120.dp).clip(CircleShape).background(Accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${result.position}",
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentInk
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${result.position}/${result.total}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Accent
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Parada ${result.position} de ${result.total}",
                            fontSize = 15.sp,
                            color = Muted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            result.address,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        if (result.ambiguous) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "📦 Essa parada tem mais de um pacote — confira se pegou todos antes de seguir.",
                                fontSize = 13.sp,
                                color = Muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is ScanLabelResult.NotFound -> {
                        Icon(
                            Icons.Default.SearchOff, contentDescription = null,
                            modifier = Modifier.size(64.dp), tint = Muted
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Nenhum pacote com esse código nesta rota",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Código lido: ${result.code}",
                            fontSize = 13.sp,
                            color = Muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )
}
