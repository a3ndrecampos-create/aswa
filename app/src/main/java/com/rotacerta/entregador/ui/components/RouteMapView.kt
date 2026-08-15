package com.rotacerta.entregador.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
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
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * Estilo de mapa mais clean/moderno que o visual clássico do OpenStreetMap (o mesmo
 * "Voyager" da CartoDB que já era usado na versão web antiga do mapa) — continua sendo
 * tiles públicos e gratuitos, só que com um visual mais minimalista.
 */
private val VoyagerTileSource: ITileSource = object : OnlineTileSourceBase(
    "CartoVoyager", 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        getBaseUrl() + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + mImageFilenameEnding
}


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
                setTileSource(VoyagerTileSource)
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

            val polyline = Polyline(mapView).apply {
                setPoints(allPoints)
                outlinePaint.color = AndroidColor.parseColor("#8B5CF6")
                outlinePaint.strokeWidth = 8f
            }
            mapView.overlays.add(polyline)

            origin?.let {
                mapView.overlays.add(pinMarker(mapView, GeoPoint(it.lat, it.lng), "🏁", "#2FA86A"))
            }
            sortedDeliveries.forEach { d ->
                mapView.overlays.add(
                    pinMarker(mapView, GeoPoint(d.lat, d.lng), d.order.toString(), "#8B5CF6", highlighted = d.order == highlightOrder)
                )
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

/** Marcador circular colorido com um texto/número no meio (mesmo visual usado antes no mapa web). */
private fun pinMarker(mapView: MapView, point: GeoPoint, label: String, hexColor: String, highlighted: Boolean = false): Marker {
    return Marker(mapView).apply {
        position = point
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = BitmapDrawable(mapView.context.resources, pinBitmap(label, hexColor, highlighted))
        setInfoWindow(null)
    }
}

private fun pinBitmap(label: String, hexColor: String, highlighted: Boolean): Bitmap {
    val sizeDp = if (highlighted) 42 else 30
    val density = 2.5f // aproximação segura sem depender de Context/Resources aqui
    val sizePx = (sizeDp * density).toInt()

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f - 4f

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor(hexColor) }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, fillPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = sizePx * 0.4f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val textY = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(label, sizePx / 2f, textY, textPaint)

    return bitmap
}
