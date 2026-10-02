package com.rotacerta.entregador.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Leitura de texto por OCR — usada como alternativa quando a etiqueta não tem
 * QR code/código de barras legível, ou quando o entregador prefere fotografar
 * o endereço escrito diretamente na etiqueta.
 */
object OcrHelper {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result.text) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    private val CEP_KEYWORD_REGEX = Regex("""(?i)cep[:\s.-]{0,4}(\d{5}-?\d{3})""")
    private val CEP_NEAR_KEYWORD_LINE_REGEX = Regex("""(?i)\bcep\b""")
    private val CEP_REGEX = Regex("""\d{5}-?\d{3}""")

    /**
     * Antes, pegava o PRIMEIRO número de 8 dígitos formatado como CEP em qualquer lugar
     * do texto — o que dava errado quando a etiqueta também tinha telefone, código de
     * rastreio ou outro número de 8 dígitos por perto. Agora prioriza achar a palavra
     * "CEP" primeiro e pegar o número logo depois dela, que é bem mais confiável.
     */
    fun extractCep(text: String): String? {
        // 1) "CEP: 01310-100" ou "CEP 01310100" — número colado na própria palavra.
        CEP_KEYWORD_REGEX.find(text)?.let { match ->
            val digits = match.groupValues[1].filter { it.isDigit() }
            if (digits.length == 8) return "${digits.substring(0, 5)}-${digits.substring(5)}"
        }

        // 2) A palavra "CEP" está numa linha e o número ficou na linha seguinte (comum
        // quando o OCR quebra a etiqueta em linhas diferentes do que o layout original).
        val lines = text.lines()
        val cepLineIndex = lines.indexOfFirst { CEP_NEAR_KEYWORD_LINE_REGEX.containsMatchIn(it) }
        if (cepLineIndex != -1) {
            val nearby = lines.drop(cepLineIndex).take(3).joinToString(" ")
            CEP_REGEX.find(nearby)?.let { match ->
                val digits = match.value.filter { it.isDigit() }
                if (digits.length == 8) return "${digits.substring(0, 5)}-${digits.substring(5)}"
            }
        }

        // 3) Último recurso (comportamento antigo): primeiro número de 8 dígitos no
        // formato de CEP em qualquer lugar do texto, mesmo sem a palavra "CEP" por perto.
        val match = CEP_REGEX.find(text) ?: return null
        val digits = match.value.filter { it.isDigit() }
        return if (digits.length == 8) "${digits.substring(0, 5)}-${digits.substring(5)}" else null
    }

    // Tenta achar o número da casa/apto na etiqueta. Prioriza a linha que tem
    // "endereço"/"endereco" (mais confiável), testando primeiro palavras-chave
    // tipo "nº", depois o formato mais comum "Rua X 284," (número antes da
    // vírgula) e por fim "Rua X, 284" (número depois da vírgula).
    // É um "melhor esforço": sempre vale conferir e corrigir manualmente.
    private val ENDERECO_LINE_REGEX = Regex("""(?i)endere[çc]o""")
    private val NUMERO_KEYWORD_REGEX = Regex("""n[ºo°.:]?\s*(\d{1,6})""", RegexOption.IGNORE_CASE)
    private val NUMERO_BEFORE_COMMA_REGEX = Regex("""(\d{1,6})\s*,""")
    private val NUMERO_AFTER_COMMA_REGEX = Regex(""",\s*(\d{1,6})\b""")

    fun extractNumero(text: String): String? {
        val enderecoLine = text.lines().firstOrNull { ENDERECO_LINE_REGEX.containsMatchIn(it) }
        if (enderecoLine != null) {
            NUMERO_KEYWORD_REGEX.find(enderecoLine)?.let { return it.groupValues[1] }
            NUMERO_BEFORE_COMMA_REGEX.find(enderecoLine)?.let { return it.groupValues[1] }
            NUMERO_AFTER_COMMA_REGEX.find(enderecoLine)?.let { return it.groupValues[1] }
        }
        NUMERO_KEYWORD_REGEX.find(text)?.let { return it.groupValues[1] }
        NUMERO_BEFORE_COMMA_REGEX.find(text)?.let { return it.groupValues[1] }
        NUMERO_AFTER_COMMA_REGEX.find(text)?.let { return it.groupValues[1] }
        return null
    }
}
