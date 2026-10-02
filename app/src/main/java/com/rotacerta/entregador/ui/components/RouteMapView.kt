package com.rotacerta.entregador.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.domain.LatLng
import com.rotacerta.entregador.ui.theme.Muted
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

// O estilo "Voyager" da CartoDB (usado antes aqui) passou a exigir uma API key deles —
// sem ela, os tiles vêm com uma marca d'água "API KEY REQUIRED" por cima do mapa inteiro.
// Isso mudou do lado da CartoDB, não é algo que dava pra prever. Trocado pelo Mapnik, o
// estilo padrão do próprio OpenStreetMap: menos "moderno" visualmente, mas garantidamente
// gratuito pra sempre — não depende de nenhuma empresa terceira que possa mudar de
// política do dia pra noite.


/**
 * Mapa da rota usando osmdroid (mapas do OpenStreetMap, renderização nativa — não é
 * WebView). Diferente do Google Maps, não precisa de nenhuma API key nem cadastro no
 * Google Cloud: os "tiles" (imagens do mapa) vêm direto dos servidores públicos do
 * OpenStreetMap, de graça.
 */
@Composable
fun RouteMap(
    deliveries: List<Delivery>,
    origin: LatLng?,
    returnPoint: LatLng?,
    roundTrip: Boolean,
    highlightOrder: Int? = null,
    onStopMarkerClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sortedDeliveries = remember(deliveries) { deliveries.sortedBy { it.order }.distinctBy { it.order } }

    val allPoints = remember(sortedDeliveries, origin, returnPoint, roundTrip) {
        buildList {
            origin?.let { add(GeoPoint(it.lat, it.lng)) }
            sortedDeliveries.forEach { add(GeoPoint(it.lat, it.lng)) }
            if (roundTrip) (returnPoint ?: origin)?.let { add(GeoPoint(it.lat, it.lng)) }
        }
    }

    if (allPoints.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Sem paradas pra mostrar no mapa.", color = Muted)
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // O OSM pede um User-Agent identificável (mesma regra de boas práticas que já
            // seguimos no Nominatim) e um diretório de cache — usar o cache interno do app
            // evita precisar de permissão de armazenamento.
            Configuration.getInstance().userAgentValue = ctx.packageName
            Configuration.getInstance().osmdroidTileCache = File(ctx.cacheDir, "osmdroid")

            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                // Os botões +/- nativos do osmdroid aparecem como um popup flutuante por
                // cima de TODA a tela (não ficam presos dentro da área do mapa) — foi isso
                // que tampava o botão "Editar sequência" ao dar zoom. Como já temos zoom
                // por pinça (setMultiTouchControls acima), desativamos esses botões extras.
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                setBuiltInZoomControls(false)
                controller.setZoom(13.0)
                val center = GeoPoint(
                    allPoints.map { it.latitude }.average(),
                    allPoints.map { it.longitude }.average()
                )
                controller.setCenter(center)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()

            // Sem linha ligando os balões: era uma linha reta "como o pássaro voa", direto
            // de ponto a ponto, sem seguir as ruas de verdade — cortava quarteirões e
            // prédios no meio, ficava confuso. Melhor só os pins no mapa.
            origin?.let {
                mapView.overlays.add(pinMarker(mapView, GeoPoint(it.lat, it.lng), "🏁", "#2FA86A"))
            }
            sortedDeliveries.forEach { d ->
                val marker = pinMarker(mapView, GeoPoint(d.lat, d.lng), d.order.toString(), "#8B5CF6", highlighted = d.order == highlightOrder)
                if (onStopMarkerClick != null) {
                    marker.setOnMarkerClickListener { _, _ ->
                        onStopMarkerClick(d.order)
                        true // não centraliza/abre balão padrão — só dispara nossa seleção
                    }
                }
                mapView.overlays.add(marker)
            }
            if (roundTrip) {
                (returnPoint ?: origin)?.let {
                    mapView.overlays.add(pinMarker(mapView, GeoPoint(it.lat, it.lng), "🏠", "#2FA86A"))
                }
            }

            if (allPoints.size > 1) {
                val bbox = BoundingBox.fromGeoPoints(allPoints)
                mapView.post {
                    runCatching { mapView.zoomToBoundingBox(bbox, true, 100) }
                }
            }
            mapView.invalidate()
        }
    )
}

/**
 * Marcador em formato de "pin/gota" (círculo com uma pontinha embaixo apontando pro
 * local exato) com sombra suave por baixo — o mesmo visual que Uber, 99 e Google Maps
 * usam. A ponta da gota é o ponto de ancoragem: é ela, não o centro do círculo, que marca
 * a coordenada exata no mapa.
 */
private fun pinMarker(mapView: MapView, point: GeoPoint, label: String, hexColor: String, highlighted: Boolean = false): Marker {
    return Marker(mapView).apply {
        position = point
        // (0.5, 1.0) = centro horizontal, borda de baixo — é onde fica a ponta da gota.
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = BitmapDrawable(mapView.context.resources, pinBitmap(label, hexColor, highlighted))
        setInfoWindow(null)
    }
}

private fun pinBitmap(label: String, hexColor: String, highlighted: Boolean): Bitmap {
    val circleDiameterDp = if (highlighted) 40 else 30
    val density = 2.5f // aproximação segura sem depender de Context/Resources aqui
    val circleD = circleDiameterDp * density
    val radius = circleD / 2f
    val tailLength = circleD * 0.55f
    val shadowPad = 10f

    val width = (circleD + shadowPad * 2).toInt()
    val height = (circleD + tailLength + shadowPad * 2).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val cy = shadowPad + radius

    // Monta o formato gota: círculo em cima + triângulo embaixo, unidos num só contorno.
    val pinPath = Path().apply {
        addCircle(cx, cy, radius, Path.Direction.CW)
        val tail = Path().apply {
            val tailHalfWidth = radius * 0.62f
            moveTo(cx - tailHalfWidth, cy + radius * 0.55f)
            lineTo(cx, cy + radius + tailLength)
            lineTo(cx + tailHalfWidth, cy + radius * 0.55f)
            close()
        }
        op(tail, Path.Op.UNION)
    }

    // Sombra suave por baixo do pin (mesmo contorno, deslocado e borrado).
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(70, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.save()
    canvas.translate(0f, 3f)
    canvas.drawPath(pinPath, shadowPaint)
    canvas.restore()

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor(hexColor) }
    canvas.drawPath(pinPath, fillPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawPath(pinPath, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = circleD * 0.42f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, cx, textY, textPaint)

    return bitmap
}
