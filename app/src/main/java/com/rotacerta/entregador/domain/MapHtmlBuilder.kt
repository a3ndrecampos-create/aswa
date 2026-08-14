package com.rotacerta.entregador.domain

import com.rotacerta.entregador.data.Delivery

/**
 * Monta o HTML do mapa (Leaflet/OpenStreetMap) usado tanto no diálogo de mapa
 * rápido (RouteMapDialog) quanto na aba Mapa (MapScreen). Ficar num só lugar
 * evita ter duas versões do mesmo HTML divergindo com o tempo.
 */
object MapHtmlBuilder {

    data class MapPoint(val lat: Double, val lng: Double, val label: String, val color: String, val highlighted: Boolean = false)

    /**
     * @param highlightOrder se preenchido, dá destaque visual (marcador maior, com pulso)
     * na parada com esse número — usado pra sincronizar "toque na lista" -> "realce no mapa".
     */
    fun build(
        deliveries: List<Delivery>,
        origin: LatLng?,
        returnPoint: LatLng?,
        roundTrip: Boolean,
        highlightOrder: Int? = null
    ): String {
        val points = mutableListOf<MapPoint>()
        origin?.let { points.add(MapPoint(it.lat, it.lng, "🏁", "#2FA86A")) }
        deliveries.sortedBy { it.order }.distinctBy { it.order }.forEach {
            points.add(MapPoint(it.lat, it.lng, it.order.toString(), "#8B5CF6", highlighted = it.order == highlightOrder))
        }
        if (roundTrip) {
            val ret = returnPoint ?: origin
            ret?.let { points.add(MapPoint(it.lat, it.lng, "🏠", "#2FA86A")) }
        }

        if (points.isEmpty()) {
            return "<html><body style='font-family:sans-serif;text-align:center;padding-top:40%;color:#888'>" +
                "Sem paradas pra mostrar no mapa. Importe ou adicione entregas primeiro.</body></html>"
        }

        val latlngs = points.joinToString(",") { "[${it.lat},${it.lng}]" }
        val markersJs = points.joinToString("\n") { p ->
            val size = if (p.highlighted) 40 else 30
            val fontSize = if (p.highlighted) 15 else 13
            val ring = if (p.highlighted) "box-shadow:0 0 0 6px rgba(139,92,246,.35), 0 1px 5px rgba(0,0,0,.45);" else "box-shadow:0 1px 5px rgba(0,0,0,.45);"
            """
            L.marker([${p.lat}, ${p.lng}], {icon: L.divIcon({
                html: '<div style="background:${p.color};color:#fff;border-radius:50%;width:${size}px;height:${size}px;display:flex;align-items:center;justify-content:center;font-weight:bold;font-size:${fontSize}px;border:2px solid white;$ring transition:all .2s;">${p.label}</div>',
                iconSize: [$size,$size], className: ''
            })}).addTo(map);
            """.trimIndent()
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0">
              <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
              <style>
                html,body,#map{height:100%;margin:0;padding:0;background:#eee;}
                #status{
                  position:fixed; top:0; left:0; right:0; bottom:0; z-index:9999;
                  display:flex; align-items:center; justify-content:center; text-align:center;
                  font-family:sans-serif; color:#555; background:#f2f2f2; padding:24px; box-sizing:border-box;
                }
                .leaflet-control-zoom { margin-top: 14px !important; }
              </style>
            </head>
            <body>
              <div id="status">Carregando mapa...</div>
              <div id="map"></div>
              <script>
                window.onerror = function(msg) {
                  document.getElementById('status').style.display = 'flex';
                  document.getElementById('status').innerHTML =
                    '⚠️ Não consegui carregar o mapa.<br><br><small>' + msg + '</small><br><br>Verifique sua conexão com a internet.';
                  return true;
                };
              </script>
              <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
              <script>
                var map = L.map('map', { zoomControl: true, tap: true });
                var tiles = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                  maxZoom: 19,
                  subdomains: 'abcd',
                  attribution: '&copy; OpenStreetMap &copy; CARTO'
                }).addTo(map);

                var pts = [$latlngs];
                var poly = L.polyline(pts, {color: '#8B5CF6', weight: 4, opacity: 0.8}).addTo(map);
                map.fitBounds(poly.getBounds(), {padding: [40,40]});
                $markersJs

                var tilesLoaded = false;
                tiles.on('load', function() {
                  tilesLoaded = true;
                  document.getElementById('status').style.display = 'none';
                });
                tiles.on('tileerror', function() {
                  document.getElementById('status').innerHTML =
                    '⚠️ Não consegui carregar as imagens do mapa.<br><br>Verifique sua conexão com a internet e tente de novo.';
                });
                setTimeout(function() {
                  if (!tilesLoaded) {
                    document.getElementById('status').innerHTML =
                      '⚠️ O mapa está demorando pra carregar.<br><br>Verifique sua conexão com a internet.';
                  }
                }, 8000);
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
