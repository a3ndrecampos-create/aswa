package com.rotacerta.entregador.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.domain.LatLng

/**
 * Mostra a rota inteira num mapa (WebView + Leaflet/OpenStreetMap — gratuito,
 * sem precisar de chave de API nem biblioteca pesada de mapa).
 * Marca a origem (🏁), cada parada numerada, e o destino final quando "ida e volta"
 * estiver ativado.
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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL(
                            "https://rotacerta.app/",
                            buildMapHtml(deliveries, origin, returnPoint, roundTrip),
                            "text/html", "UTF-8", null
                        )
                    }
                }
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

private fun buildMapHtml(deliveries: List<Delivery>, origin: LatLng?, returnPoint: LatLng?, roundTrip: Boolean): String {
    data class Point(val lat: Double, val lng: Double, val label: String, val color: String)

    val points = mutableListOf<Point>()
    origin?.let { points.add(Point(it.lat, it.lng, "🏁", "#2FA86A")) }
    deliveries.sortedBy { it.order }.distinctBy { it.order }.forEach {
        points.add(Point(it.lat, it.lng, it.order.toString(), "#8B5CF6"))
    }
    if (roundTrip) {
        val ret = returnPoint ?: origin
        ret?.let { points.add(Point(it.lat, it.lng, "🏠", "#2FA86A")) }
    }

    if (points.isEmpty()) {
        return "<html><body style='font-family:sans-serif;text-align:center;padding-top:40%;color:#888'>Sem paradas pra mostrar no mapa.</body></html>"
    }

    val latlngs = points.joinToString(",") { "[${it.lat},${it.lng}]" }
    val markersJs = points.joinToString("\n") { p ->
        """
        L.marker([${p.lat}, ${p.lng}], {icon: L.divIcon({
            html: '<div style="background:${p.color};color:#fff;border-radius:50%;width:30px;height:30px;display:flex;align-items:center;justify-content:center;font-weight:bold;font-size:13px;border:2px solid white;box-shadow:0 1px 5px rgba(0,0,0,.45);">${p.label}</div>',
            iconSize: [30,30], className: ''
        })}).addTo(map);
        """.trimIndent()
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
          <style>html,body,#map{height:100%;margin:0;padding:0;background:#eee;}</style>
        </head>
        <body>
          <div id="map"></div>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <script>
            var map = L.map('map');
            L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
              maxZoom: 19,
              subdomains: 'abcd',
              attribution: '&copy; OpenStreetMap &copy; CARTO'
            }).addTo(map);
            var pts = [$latlngs];
            var poly = L.polyline(pts, {color: '#8B5CF6', weight: 4, opacity: 0.8}).addTo(map);
            map.fitBounds(poly.getBounds(), {padding: [40,40]});
            $markersJs
          </script>
        </body>
        </html>
    """.trimIndent()
}
