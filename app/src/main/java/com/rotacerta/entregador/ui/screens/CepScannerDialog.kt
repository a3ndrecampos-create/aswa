package com.rotacerta.entregador.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rotacerta.entregador.ocr.OcrHelper

/**
 * Câmera embutida que lê texto (OCR) até achar um CEP na etiqueta, e ao mesmo
 * tempo tenta ler um QR code/código de barras que esteja no mesmo enquadramento
 * (código de rastreio do pacote). Só aceita o CEP depois de ler o MESMO valor
 * duas vezes seguidas — evita fechar com um número errado por causa de um frame
 * borrado/rápido demais.
 * Chama onResult(cep, numero, trackingCode) — numero e trackingCode podem vir
 * nulos se não achar (usuário completa à mão).
 */
@Composable
fun CepScannerDialog(onResult: (cep: String, numero: String?, trackingCode: String?) -> Unit, onDismiss: () -> Unit) {
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    var hasResult by remember { mutableStateOf(false) }
    var candidateCep by remember { mutableStateOf<String?>(null) }
    var candidateNumero by remember { mutableStateOf<String?>(null) }
    var trackingCode by remember { mutableStateOf<String?>(null) }
    var lastFrameAt by remember { mutableStateOf(0L) }
    var instructions by remember { mutableStateOf("Aponte para o CEP na etiqueta") }

    EmbeddedScannerDialog(
        instructions = instructions,
        found = candidateCep != null,
        onFrame = { imageProxy ->
            val now = System.currentTimeMillis()
            val mediaImage = imageProxy.image
            if (mediaImage != null && !hasResult && now - lastFrameAt > 350) {
                lastFrameAt = now
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                // Tenta pegar o código de rastreio (QR/código de barras) se estiver visível —
                // não bloqueia nem precisa ser encontrado pra continuar
                if (trackingCode == null) {
                    barcodeScanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            barcodes.firstOrNull()?.rawValue?.let { trackingCode = it }
                        }
                }

                textRecognizer.process(image)
                    .addOnSuccessListener { result ->
                        val text = result.text
                        val cep = OcrHelper.extractCep(text)
                        if (cep != null && !hasResult) {
                            if (cep == candidateCep) {
                                hasResult = true
                                onResult(cep, candidateNumero ?: OcrHelper.extractNumero(text), trackingCode)
                            } else {
                                candidateCep = cep
                                candidateNumero = OcrHelper.extractNumero(text)
                                instructions = "Achei $cep — confirmando..."
                            }
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
