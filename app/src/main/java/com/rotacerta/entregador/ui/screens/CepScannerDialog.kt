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
 * Quando acha, também tenta achar o número da casa perto do texto, e chama
 * onResult(cep, numero) — numero pode vir nulo se não achar (usuário completa à mão).
 */
@Composable
fun CepScannerDialog(onResult: (cep: String, numero: String?) -> Unit, onDismiss: () -> Unit) {
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var hasResult by remember { mutableStateOf(false) }

    EmbeddedScannerDialog(
        instructions = "Aponte para o CEP na etiqueta",
        onFrame = { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage != null && !hasResult) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val text = result.text
                        val cep = OcrHelper.extractCep(text)
                        if (cep != null && !hasResult) {
                            hasResult = true
                            onResult(cep, OcrHelper.extractNumero(text))
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
