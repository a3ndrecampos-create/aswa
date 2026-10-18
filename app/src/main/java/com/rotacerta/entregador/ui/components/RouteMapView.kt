package com.rotacerta.entregador.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rotacerta.entregador.data.Delivery
import com.rotacerta.entregador.domain.LatLng
import com.rotacerta.entregador.ui.theme.Muted
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MLatLng

// Mapa vetorial (GPU) do OpenFreeMap: dados do OpenStreetMap, estilo claro e limpo, grátis,
// sem chave de API e sem cadastro. Outros estilos prontos: .../styles/bright e .../styles/liberty
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

private const val PIN_COLOR = "#2563EB"
private const val END_COLOR = "#16A34A"
private const val CLUSTER_COLOR = "#0F172A"

private const val SRC_STOPS = "stops-src"
private const val SRC_ENDS = "ends-src"
private const val LAYER_CLUSTER = "cluster-layer"
private const val LAYER_STOP = "stop-layer"
private const val LAYER_ENDS = "ends-layer"

private class MapHolder {
    var map: MapLibreMap? = null
    var style: Style? = null
    var sorted: List<Delivery> = emptyList()
    var origin: LatLng? = null
    var home: LatLng? = null
    var highlight: Int? = null
    var onStopClick: ((Int) -> Unit)? = null
    var padL = 0; var padT = 0; var padR = 0; var padB = 0
    var fittedKey: String? = null
    var view: MapView? = null
}

/**
 * Mapa da rota com MapLibre. Sem linhas ligando os pontos: só pins numerados, agrupados em
 * bolhas "+N" quando ficam apertados (o agrupamento é feito pela GPU, então o zoom fica
 * fluido). Tocar numa bolha aproxima o mapa; tocar num pin chama [onStopMarkerClick].
 */
@Composable
fun RouteMap(
    deliveries: List<Delivery>,
    origin: LatLng?,
    returnPoint: LatLng?,
    roundTrip: Boolean,
    highlightOrder: Int? = null,
    onStopMarkerClick: ((Int) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val sorted = remember(deliveries) { deliveries.sortedBy { it.order }.distinctBy { it.order } }
    val home = if (roundTrip) (returnPoint ?: origin) else null

    if (sorted.isEmpty() && origin == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Sem paradas pra mostrar no mapa.", color = Muted)
        }
        return
    }

    val holder = remember { MapHolder() }
    holder.sorted = sorted
    holder.origin = origin
    holder.home = home
    holder.highlight = highlightOrder
    holder.onStopClick = onStopMarkerClick
    val dir = LocalLayoutDirection.current
    holder.padL = (contentPadding.calculateLeftPadding(dir).value * density).toInt()
    holder.padR = (contentPadding.calculateRightPadding(dir).value * density).toInt()
    holder.padT = (contentPadding.calculateTopPadding().value * density).toInt()
    holder.padB = (contentPadding.calculateBottomPadding().value * density).toInt()

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).also { it.onCreate(null); holder.view = it }
    }

    // Repassa o ciclo de vida da tela pro MapView (obrigatório no MapLibre).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                holder.map = map
                map.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isLogoEnabled = false
                }
                val first = holder.sorted.firstOrNull()?.let { MLatLng(it.lat, it.lng) }
                    ?: holder.origin?.let { MLatLng(it.lat, it.lng) }
                if (first != null) {
                    map.cameraPosition = CameraPosition.Builder().target(first).zoom(13.0).build()
                }
                map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
                    holder.style = style
                    setupLayers(style)
                    render(holder, mapView.context)
                    // Se o mapa ainda não tinha tamanho, enquadra assim que ele tiver.
                    mapView.post { render(holder, mapView.context) }
                }
                map.addOnMapClickListener { latLng ->
                    handleClick(map, latLng, holder, mapView.context)
                }
            }
            mapView
        },
        update = { render(holder, context) }
    )
}

private fun setupLayers(style: Style) {
    style.addSource(
        GeoJsonSource(
            SRC_STOPS, FeatureCollection.fromFeatures(emptyList<Feature>()),
            GeoJsonOptions().withCluster(true).withClusterRadius(48).withClusterMaxZoom(17)
        )
    )
    style.addSource(GeoJsonSource(SRC_ENDS, FeatureCollection.fromFeatures(emptyList<Feature>())))

    style.addLayer(
        SymbolLayer(LAYER_ENDS, SRC_ENDS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_CLUSTER, SRC_STOPS).withProperties(
            PropertyFactory.iconImage(
                Expression.concat(Expression.literal("cl-"), Expression.toString(Expression.get("point_count")))
            ),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        ).apply { setFilter(Expression.has("point_count")) }
    )
    style.addLayer(
        SymbolLayer(LAYER_STOP, SRC_STOPS).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.symbolSortKey(Expression.get("sk"))
        ).apply { setFilter(Expression.not(Expression.has("point_count"))) }
    )
}

private fun ensureImage(style: Style, name: String, make: () -> Bitmap) {
    if (style.getImage(name) == null) style.addImage(name, make())
}

private fun render(h: MapHolder, ctx: Context) {
    val map = h.map ?: return
    val style = h.style ?: return
    if (!style.isFullyLoaded) return
    val density = ctx.resources.displayMetrics.density

    ensureImage(style, "origin") { pinBitmap("🏁", END_COLOR, false, density) }
    ensureImage(style, "home") { pinBitmap("🏠", END_COLOR, false, density) }
    for (d in h.sorted) {
        ensureImage(style, "pin-${d.order}") { pinBitmap(d.order.toString(), PIN_COLOR, false, density) }
        ensureImage(style, "pinh-${d.order}") { pinBitmap(d.order.toString(), PIN_COLOR, true, density) }
    }
    for (k in 2..maxOf(2, h.sorted.size)) {
        ensureImage(style, "cl-$k") { clusterBitmap(k, density) }
    }

    val stopFeatures = h.sorted.map { d ->
        val hl = d.order == h.highlight
        Feature.fromGeometry(Point.fromLngLat(d.lng, d.lat)).also {
            it.addNumberProperty("order", d.order)
            it.addStringProperty("icon", if (hl) "pinh-${d.order}" else "pin-${d.order}")
            it.addNumberProperty("sk", if (hl) 1 else 0)
        }
    }
    (style.getSource(SRC_STOPS) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(stopFeatures))

    val endFeatures = buildList {
        h.origin?.let { add(Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)).also { f -> f.addStringProperty("icon", "origin") }) }
        h.home?.let { add(Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)).also { f -> f.addStringProperty("icon", "home") }) }
    }
    (style.getSource(SRC_ENDS) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(endFeatures))

    // Enquadra todas as paradas só quando o conjunto de pontos muda (não a cada destaque).
    val pts = buildList {
        h.origin?.let { add(MLatLng(it.lat, it.lng)) }
        h.sorted.forEach { add(MLatLng(it.lat, it.lng)) }
        h.home?.let { add(MLatLng(it.lat, it.lng)) }
    }
    val key = pts.joinToString("|") { "%.5f,%.5f".format(it.latitude, it.longitude) }
    val viewReady = (h.view?.width ?: 0) > 0 && (h.view?.height ?: 0) > 0
    if (key != h.fittedKey && pts.isNotEmpty() && viewReady) {
        h.fittedKey = key
        runCatching {
            if (pts.size == 1) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(pts[0], 16.0))
            } else {
                val b = LatLngBounds.Builder().includes(pts).build()
                val m = (48 * density).toInt()
                map.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(b, h.padL + m, h.padT + m, h.padR + m, h.padB + m)
                )
            }
        }
    }
}

private fun handleClick(map: MapLibreMap, latLng: MLatLng, h: MapHolder, ctx: Context): Boolean {
    val density = ctx.resources.displayMetrics.density
    val p = map.projection.toScreenLocation(latLng)
    val r = 26 * density
    // Os pins têm a ponta embaixo: a área de toque é deslocada um pouco pra cima.
    val rect = RectF(p.x - r, p.y - r * 1.6f, p.x + r, p.y + r * 0.6f)

    val clusters = map.queryRenderedFeatures(rect, LAYER_CLUSTER)
    if (clusters.isNotEmpty()) {
        val g = clusters[0].geometry() as? Point ?: return false
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                MLatLng(g.latitude(), g.longitude()), map.cameraPosition.zoom + 2.5
            )
        )
        return true
    }
    val stops = map.queryRenderedFeatures(rect, LAYER_STOP)
    if (stops.isNotEmpty()) {
        val order = stops[0].getNumberProperty("order")?.toInt() ?: return false
        h.onStopClick?.invoke(order)
        return h.onStopClick != null
    }
    return false
}

private fun clusterBitmap(count: Int, density: Float): Bitmap {
    val diameter = 44 * density
    val pad = 8f
    val size = (diameter + pad * 2).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val c = size / 2f
    val r = diameter / 2f

    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(60, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawCircle(c, c + 3f, r, shadow)
    canvas.drawCircle(c, c, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor(CLUSTER_COLOR) })
    canvas.drawCircle(c, c, r - 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
    })
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE; textSize = diameter * 0.34f
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    canvas.drawText("+$count", c, c - (text.descent() + text.ascent()) / 2f, text)
    return bitmap
}

/** Pin em formato de gota; a ponta (borda inferior) é o ponto exato no mapa. */
private fun pinBitmap(label: String, hexColor: String, highlighted: Boolean, density: Float): Bitmap {
    val circleD = (if (highlighted) 40 else 30) * density
    val radius = circleD / 2f
    val tail = circleD * 0.5f
    val pad = 8f
    val w = (circleD + pad * 2).toInt()
    val h = (circleD + tail + pad * 2).toInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = w / 2f
    val cy = pad + radius

    val pin = Path().apply {
        addCircle(cx, cy, radius, Path.Direction.CW)
        val t = Path().apply {
            val hw = radius * 0.62f
            moveTo(cx - hw, cy + radius * 0.55f)
            lineTo(cx, cy + radius + tail)
            lineTo(cx + hw, cy + radius * 0.55f)
            close()
        }
        op(t, Path.Op.UNION)
    }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(60, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.save(); canvas.translate(0f, 3f); canvas.drawPath(pin, shadow); canvas.restore()
    canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AndroidColor.parseColor(hexColor) })
    canvas.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
    })
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE; textSize = circleD * 0.42f
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2f, text)
    return bitmap
}
