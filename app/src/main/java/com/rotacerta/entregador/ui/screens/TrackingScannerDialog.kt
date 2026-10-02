package com.rotacerta.entregador.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.rotacerta.entregador.utils.ZxingDecoder

/**
 * Escaneia o QR code / código de barras da etiqueta (código de rastreio do pacote)
 * e devolve o valor lido em onResult. Fecha sozinho assim que encontra um código.
 *
 * Usa ZXing (não ML Kit) — veja o comentário em ZxingDecoder.kt sobre o motivo
 * (compatibilidade com a exigência de 16KB de page size do Google Play).
 */
@Composable
fun TrackingScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    var hasResult by remember { mutableStateOf(false) }

    EmbeddedScannerDialog(
        instructions = "Aponte para o QR code / código de barras do pacote",
        found = hasResult,
        onFrame = { imageProxy ->
            if (!hasResult) {
                val value = ZxingDecoder.decode(imageProxy)
                if (value != null && !hasResult) {
                    hasResult = true
                    onResult(value)
                }
            }
            imageProxy.close()
        },
        onDismiss = onDismiss
    )
}
