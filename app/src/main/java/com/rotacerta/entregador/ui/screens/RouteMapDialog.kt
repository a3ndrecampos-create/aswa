package com.rotacerta.entregador.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.domain.LatLng
import com.rotacerta.entregador.ui.components.RouteMap

/**
 * Mostra a rota inteira num mapa nativo (OpenStreetMap via osmdroid). Marca a origem
 * (🏁), cada parada numerada, e o destino final quando "ida e volta" estiver ativado.
 */
@Composable
fun RouteMapDialog(
    deliveries: List<Delivery>,
    origin: LatLng?,
    returnPoint: LatLng?,
    roundTrip: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            RouteMap(
                deliveries = deliveries,
                origin = origin,
                returnPoint = returnPoint,
                roundTrip = roundTrip,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Black)
            }
        }
    }
}
