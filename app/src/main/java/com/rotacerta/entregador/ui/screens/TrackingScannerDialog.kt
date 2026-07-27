package com.rotacerta.entregador.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/**
 * Escaneia o QR code / código de barras da etiqueta (código de rastreio do pacote)
 * e devolve o valor lido em onResult. Fecha sozinho assim que encontra um código.
 */
@Composable
fun TrackingScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val scanner = remember { BarcodeScanning.getClient() }
    var hasResult by remember { mutableStateOf(false) }

    EmbeddedScannerDialog(
        instructions = "Aponte para o QR code / código de barras do pacote",
        onFrame = { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null && !hasResult) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val value = barcodes.firstOrNull()?.rawValue
                        if (value != null && !hasResult) {
                            hasResult = true
                            onResult(value)
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        },
        onDismiss = onDismiss
    )
}
