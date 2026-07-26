package com.rotacerta.entregador.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rotacerta.entregador.ocr.OcrHelper

/**
 * Câmera embutida que fica lendo texto (OCR) até achar um CEP na etiqueta.
 * Só aceita depois de ler o MESMO CEP duas vezes seguidas — evita fechar com
 * um número errado por causa de um frame borrado/rápido demais.
 * Quando confirma, também tenta achar o número da casa perto do texto, e chama
 * onResult(cep, numero) — numero pode vir nulo se não achar (usuário completa à mão).
 */
@Composable
fun CepScannerDialog(onResult: (cep: String, numero: String?) -> Unit, onDismiss: () -> Unit) {
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var hasResult by remember { mutableStateOf(false) }
    var candidateCep by remember { mutableStateOf<String?>(null) }
    var candidateNumero by remember { mutableStateOf<String?>(null) }
    var lastFrameAt by remember { mutableStateOf(0L) }
    var instructions by remember { mutableStateOf("Aponte para o CEP na etiqueta") }

    EmbeddedScannerDialog(
        instructions = instructions,
        onFrame = { imageProxy ->
            val now = System.currentTimeMillis()
            val mediaImage = imageProxy.image
            // dá um respiro entre leituras pra câmera focar melhor (evita ler frame borrado)
            if (mediaImage != null && !hasResult && now - lastFrameAt > 350) {
                lastFrameAt = now
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val text = result.text
                        val cep = OcrHelper.extractCep(text)
                        if (cep != null && !hasResult) {
                            if (cep == candidateCep) {
                                // segunda leitura igual -> confirma
                                hasResult = true
                                onResult(cep, candidateNumero ?: OcrHelper.extractNumero(text))
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
