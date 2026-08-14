package com.rotacerta.entregador.ui.screens

import android.webkit.WebSettings
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
import com.rotacerta.entregador.domain.MapHtmlBuilder

/**
 * Mostra a rota inteira num mapa (WebView + Leaflet/OpenStreetMap — gratuito,
 * sem precisar de chave de API nem biblioteca pesada de mapa).
 * Marca a origem (🏁), cada parada numerada, e o destino final quando "ida e volta"
 * estiver ativado.
 *
 * A própria página HTML mostra "Carregando mapa..." e, se depois de alguns
 * segundos nada aparecer (sem internet, CDN bloqueado, etc.), troca pra uma
 * mensagem de erro explicando o motivo — em vez de ficar uma tela branca muda.
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
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        // Alguns provedores de mapa recusam ou tratam diferente o user-agent
                        // padrão da WebView — usar um user-agent comum de navegador evita isso.
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/120.0.0.0 Mobile Safari/537.36"
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL(
                            "https://rotacerta.app/",
                            MapHtmlBuilder.build(deliveries, origin, returnPoint, roundTrip),
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

