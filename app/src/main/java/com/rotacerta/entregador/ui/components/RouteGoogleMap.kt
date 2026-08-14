package com.rotacerta.entregador.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.domain.LatLng
import com.rotacerta.entregador.ui.theme.Accent
import com.rotacerta.entregador.ui.theme.AccentInk
import com.rotacerta.entregador.ui.theme.Muted
import com.rotacerta.entregador.ui.theme.Success
import com.google.android.gms.maps.model.LatLng as GmsLatLng

/**
 * Mapa da rota usando o SDK nativo do Google Maps (substitui a versão antiga baseada em
 * WebView + Leaflet). Vantagens práticas: não depende de nenhum CDN externo além do
 * próprio Google, renderiza de verdade em vez de recarregar uma página HTML inteira a
 * cada mudança, e dá erro/aviso de rede de forma nativa e confiável.
 *
 * Requer uma API Key do Google Maps configurada em local.properties (MAPS_API_KEY=...) —
 * veja o comentário em app/build.gradle.kts. Sem a chave, o Google mostra um aviso de
 * "for developers" por cima do mapa em vez de travar o app.
 */
@Composable
fun RouteGoogleMap(
    deliveries: List<Delivery>,
    origin: LatLng?,
    returnPoint: LatLng?,
    roundTrip: Boolean,
    highlightOrder: Int? = null,
    modifier: Modifier = Modifier
) {
    val sortedDeliveries = remember(deliveries) { deliveries.sortedBy { it.order }.distinctBy { it.order } }

    val allPoints = remember(sortedDeliveries, origin, returnPoint, roundTrip) {
        buildList {
            origin?.let { add(GmsLatLng(it.lat, it.lng)) }
            sortedDeliveries.forEach { add(GmsLatLng(it.lat, it.lng)) }
            if (roundTrip) (returnPoint ?: origin)?.let { add(GmsLatLng(it.lat, it.lng)) }
        }
    }

    if (allPoints.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Sem paradas pra mostrar no mapa.", color = Muted)
        }
        return
    }

    val cameraPositionState = rememberCameraPositionState()

    Box(modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false, mapToolbarEnabled = false),
            onMapLoaded = {
                runCatching {
                    val bounds = LatLngBounds.Builder().apply { allPoints.forEach { include(it) } }.build()
                    cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }.onFailure {
                    // Acontece quando só existe 1 ponto (bounds "vazio") — nesse caso, centraliza
                    // nele com um zoom razoável em vez de tentar enquadrar uma área.
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(allPoints.first(), 15f))
                }
            }
        ) {
            origin?.let {
                MarkerComposable(state = MarkerState(position = GmsLatLng(it.lat, it.lng))) {
                    EmojiPin("🏁")
                }
            }
            sortedDeliveries.forEach { d ->
                MarkerComposable(
                    state = MarkerState(position = GmsLatLng(d.lat, d.lng)),
                    anchor = Offset(0.5f, 0.5f)
                ) {
                    NumberPin(d.order, highlighted = d.order == highlightOrder)
                }
            }
            if (roundTrip) {
                (returnPoint ?: origin)?.let {
                    MarkerComposable(state = MarkerState(position = GmsLatLng(it.lat, it.lng))) {
                        EmojiPin("🏠")
                    }
                }
            }
            Polyline(points = allPoints, color = Accent, width = 8f)
        }
    }
}

@Composable
private fun NumberPin(number: Int, highlighted: Boolean) {
    val size = if (highlighted) 38.dp else 28.dp
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Accent)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            color = AccentInk,
            fontWeight = FontWeight.Bold,
            fontSize = if (highlighted) 15.sp else 12.sp
        )
    }
}

@Composable
private fun EmojiPin(emoji: String) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Success)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 13.sp)
    }
}
