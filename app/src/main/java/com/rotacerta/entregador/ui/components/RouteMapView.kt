package com.rotacerta.entregador.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
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
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
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

/** Guarda os dados mais recentes pra reconstruir os marcadores a qualquer momento — tanto
 *  quando os dados mudam (Compose) quanto quando só o zoom/posição do mapa muda (gesto
 *  nativo, que o Compose nem fica sabendo que aconteceu). */
private class MapRenderState {
    var sortedDeliveries: List<Delivery> = emptyList()
    var origin: LatLng? = null
    var returnPoint: LatLng? = null
    var roundTrip: Boolean = false
    var highlightOrder: Int? = null
    var onStopMarkerClick: ((Int) -> Unit)? = null
}

/**
 * Mapa da rota usando osmdroid (mapas do OpenStreetMap, renderização nativa — não é
 * WebView). Diferente do Google Maps, não precisa de nenhuma API key nem cadastro no
 * Google Cloud: os "tiles" (imagens do mapa) vêm direto dos servidores públicos do
 * OpenStreetMap, de graça.
 *
 * As paradas próximas na tela (não no mapa real — na TELA, que muda com o zoom) são
 * agrupadas num "bolhão" com o número de paradas ali dentro, em vez de ficarem uma por
 * cima da outra — mesmo comportamento do Uber/Google Maps. Dando zoom ou tocando no
 * bolhão, ele se separa nos pins individuais.
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

    val renderState = remember { MapRenderState() }
    renderState.sortedDeliveries = sortedDeliveries
    renderState.origin = origin
    renderState.returnPoint = returnPoint
    renderState.roundTrip = roundTrip
    renderState.highlightOrder = highlightOrder
    renderState.onStopMarkerClick = onStopMarkerClick

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

                // Refaz os pins sempre que o usuário dá zoom ou arrasta o mapa — é só
                // nesse momento que dá pra saber quais paradas ficaram perto na TELA (a
                // mesma distância real em metros fica mais apertada ou mais espaçada na
                // tela dependendo do zoom).
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        rebuildMarkers(this@apply, renderState)
                        return false
                    }
                    override fun onZoom(event: ZoomEvent?): Boolean {
                        rebuildMarkers(this@apply, renderState)
                        return false
                    }
                })
            }
        },
        update = { mapView ->
            rebuildMarkers(mapView, renderState)

            if (allPoints.size > 1) {
                val bbox = BoundingBox.fromGeoPoints(allPoints)
                mapView.post {
                    runCatching { mapView.zoomToBoundingBox(bbox, true, 100) }
                }
            }
        }
    )
}

/** Raio em pixels de tela: paradas mais perto que isso uma da outra viram um bolhão único. */
private const val CLUSTER_RADIUS_PX = 55.0

private fun rebuildMarkers(mapView: MapView, state: MapRenderState) {
    mapView.overlays.clear()

    state.origin?.let {
        mapView.overlays.add(pinMarker(mapView, GeoPoint(it.lat, it.lng), "🏁", "#2FA86A"))
    }

    // Agrupamento simples: projeta cada parada pra posição em pixels na tela atual, e junta
    // (algoritmo guloso) quem está a menos de CLUSTER_RADIUS_PX de distância uma da outra.
    val projection = mapView.projection
    data class Entry(val delivery: Delivery, val screenX: Int, val screenY: Int)
    val entries = state.sortedDeliveries.map { d ->
        val pt = Point()
        projection.toPixels(GeoPoint(d.lat, d.lng), pt)
        Entry(d, pt.x, pt.y)
    }

    val used = BooleanArray(entries.size)
    for (i in entries.indices) {
        if (used[i]) continue
        val group = mutableListOf(entries[i])
        used[i] = true
        for (j in i + 1 until entries.size) {
            if (used[j]) continue
            val dx = (entries[i].screenX - entries[j].screenX).toDouble()
            val dy = (entries[i].screenY - entries[j].screenY).toDouble()
            if (dx * dx + dy * dy < CLUSTER_RADIUS_PX * CLUSTER_RADIUS_PX) {
                group.add(entries[j])
                used[j] = true
            }
        }

        if (group.size == 1) {
            val d = group[0].delivery
            val marker = pinMarker(
                mapView, GeoPoint(d.lat, d.lng), d.order.toString(), "#8B5CF6",
                highlighted = d.order == state.highlightOrder
            )
            state.onStopMarkerClick?.let { onClick ->
                marker.setOnMarkerClickListener { _, _ -> onClick(d.order); true }
            }
            mapView.overlays.add(marker)
        } else {
            val avgLat = group.map { it.delivery.lat }.average()
            val avgLng = group.map { it.delivery.lng }.average()
            // Visual BEM diferente do pin de parada (círculo cheio, sem pontinha, com "+"
            // na frente do número) — com o mesmo formato de pin, "2" ou "3" aqui parecia a
            // ORDEM da parada repetida, dando a impressão de rota errada quando na
            // verdade é só "tem 2/3 paradas perto daqui".
            val clusterMarker = clusterMarker(mapView, GeoPoint(avgLat, avgLng), group.size)
            // Tocar no bolhão aproxima o mapa ali, separando as paradas de dentro dele.
            clusterMarker.setOnMarkerClickListener { _, mv ->
                mv.controller.animateTo(GeoPoint(avgLat, avgLng))
                mv.controller.zoomTo((mv.zoomLevelDouble + 2.5).coerceAtMost(20.0))
                true
            }
            mapView.overlays.add(clusterMarker)
        }
    }

    if (state.roundTrip) {
        (state.returnPoint ?: state.origin)?.let {
            mapView.overlays.add(pinMarker(mapView, GeoPoint(it.lat, it.lng), "🏠", "#2FA86A"))
        }
    }

    mapView.invalidate()
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

/**
 * Bolhão de agrupamento: círculo CHEIO, sem pontinha embaixo (porque não representa um
 * local exato, representa uma área com várias paradas dentro) — visual propositalmente
 * diferente do pin de parada, pra nunca ser confundido com o número de ordem da rota.
 */
private fun clusterMarker(mapView: MapView, point: GeoPoint, count: Int): Marker {
    return Marker(mapView).apply {
        position = point
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = BitmapDrawable(mapView.context.resources, clusterBitmap(count))
        setInfoWindow(null)
    }
}

private fun clusterBitmap(count: Int): Bitmap {
    val density = 2.5f
    val diameter = 46 * density
    val shadowPad = 10f
    val size = (diameter + shadowPad * 2).toInt()

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val radius = diameter / 2f

    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(70, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.save()
    canvas.translate(0f, 3f)
    canvas.drawCircle(cx, cy, radius, shadowPaint)
    canvas.restore()

    // Cor sólida escura (não é o mesmo roxo dos pins de parada) + anel branco duplo —
    // reforça visualmente "isto é outra coisa, não é uma parada".
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor("#1F2937") }
    canvas.drawCircle(cx, cy, radius, fillPaint)

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(cx, cy, radius - 4f, ringPaint)

    val label = "+$count"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = diameter * 0.34f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, cx, textY, textPaint)

    return bitmap
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
